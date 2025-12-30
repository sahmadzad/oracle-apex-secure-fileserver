package tokenrest;

/**
 * REST endpoint for creating temporary access tokens to documents with APEX session validation
 * @author Saeed Ahmadzad-Asl
 */

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path as NioPath;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Path("/AccessToDocumentV2")
public class AccessToDocumentV2 {

    // ============================================
    // CONSTANTS & CONFIGURATION
    // ============================================

    private static final Logger LOGGER = Logger.getLogger(AccessToDocumentV2.class.getName());
    private static final String PATH_CONFIG_FILE = "/u01/oracle/config/document-paths.properties";
    private static final Properties DOCUMENT_PATHS = new Properties();
    
    // Header constants
    private static final String HEADER_DOC_ID = "X-Doc-Id";
    private static final String HEADER_FILE_NAME = "X-File-Name";
    private static final String HEADER_DOC_TYPE = "X-Doc-Type";
    private static final String HEADER_APP_ID = "p_app_id";
    private static final String HEADER_SESSION_ID = "p_session_id";
    
    // Configuration keys
    private static final String KEY_TEMP_BASE_PATH = "TEMP_BASE_PATH";
    private static final String KEY_TEMP_EXPIRATION = "TEMP_FILE_EXPIRATION_MS";
    private static final String KEY_ORDS_VALIDATION = "ORDS_VALIDATE_SESSION";
    
    // Response messages
    private static final String MSG_MISSING_HEADERS = "Missing required headers";
    private static final String MSG_INVALID_SESSION = "Invalid or expired APEX session";
    private static final String MSG_INVALID_DOC_TYPE = "Invalid document type";
    private static final String MSG_FILE_NOT_FOUND = "File not found";
    private static final String MSG_SUCCESS = "OK";
    
    // JSON keys
    private static final String JSON_KEY_ERROR = "error";
    private static final String JSON_KEY_STATUS = "status";
    private static final String JSON_KEY_TOKEN = "token";
    private static final String JSON_KEY_FILE = "file";
    
    // Thread pool for async cleanup
    private static final ExecutorService CLEANUP_EXECUTOR = Executors.newFixedThreadPool(1);
    
    // Configuration values
    private static String TEMP_BASE_DIR;
    private static long TEMP_FILE_EXPIRATION_MS;
    private static String ORDS_VALIDATE_URL;

    static {
        loadConfiguration();
    }

    // ============================================
    // CONFIGURATION LOADER
    // ============================================

    private static void loadConfiguration() {
        try {
            File configFile = new File(PATH_CONFIG_FILE);
            LOGGER.info("Loading document paths from: " + configFile.getAbsolutePath());

            try (InputStream inputStream = new FileInputStream(configFile)) {
                DOCUMENT_PATHS.load(inputStream);
            }

            // Load configuration values
            TEMP_BASE_DIR = DOCUMENT_PATHS.getProperty(KEY_TEMP_BASE_PATH, "/tmp/document-access");
            TEMP_FILE_EXPIRATION_MS = Long.parseLong(
                DOCUMENT_PATHS.getProperty(KEY_TEMP_EXPIRATION, "3600000")
            );
            ORDS_VALIDATE_URL = DOCUMENT_PATHS.getProperty(KEY_ORDS_VALIDATION);
            
            LOGGER.info("Configuration loaded successfully:");
            LOGGER.info("- Temp directory: " + TEMP_BASE_DIR);
            LOGGER.info("- Temp expiration: " + TEMP_FILE_EXPIRATION_MS + " ms");
            LOGGER.info("- ORDS URL: " + ORDS_VALIDATE_URL);
            
        } catch (Exception e) {
            LOGGER.severe("Failed to load configuration file: " + PATH_CONFIG_FILE + 
                         " - Error: " + e.getMessage());
        }
    }

    // ============================================
    // CORS PREFLIGHT HANDLER
    // ============================================

    @OPTIONS
    @Path("/AccessToDocumentV2")
    public Response handleCorsPreflight() {
        return Response.ok()
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "POST, OPTIONS")
                .header("Access-Control-Allow-Headers", 
                        "X-Doc-Id, X-File-Name, X-Doc-Type, p_app_id, p_session_id, Content-Type")
                .build();
    }

    // ============================================
    // MAIN ENDPOINT - CREATE TEMP ACCESS TOKEN
    // ============================================

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response createTemporaryAccess(
            @HeaderParam(HEADER_DOC_ID) String documentId,
            @HeaderParam(HEADER_FILE_NAME) String fileName,
            @HeaderParam(HEADER_DOC_TYPE) String documentType,
            @HeaderParam(HEADER_APP_ID) String applicationId,
            @HeaderParam(HEADER_SESSION_ID) String sessionId) {

        LOGGER.info("========= AccessToDocumentV2 - Start Processing =========");
        logRequestHeaders(documentId, fileName, documentType, applicationId, sessionId);

        try {
            // Validate required headers
            if (!validateHeaders(documentId, fileName, documentType, applicationId, sessionId)) {
                LOGGER.warning("Validation failed: Missing required headers");
                return buildErrorResponse(Response.Status.BAD_REQUEST, MSG_MISSING_HEADERS);
            }

            // Sanitize filename
            String sanitizedFileName = sanitizeFileName(fileName);

            // Validate APEX session
            if (!isApexSessionValid(applicationId, sessionId)) {
                LOGGER.warning("APEX session validation failed for appId: " + applicationId);
                return buildErrorResponse(Response.Status.UNAUTHORIZED, MSG_INVALID_SESSION);
            }

            LOGGER.info("APEX session validated successfully");

            // Resolve source file path
            String sourceBasePath = DOCUMENT_PATHS.getProperty(documentType);
            if (sourceBasePath == null) {
                LOGGER.warning("No path configuration found for document type: " + documentType);
                return buildErrorResponse(Response.Status.BAD_REQUEST, MSG_INVALID_DOC_TYPE);
            }

            // Check if source file exists
            File sourceFile = new File(sourceBasePath, documentId + "_" + sanitizedFileName);
            if (!sourceFile.exists() || !sourceFile.isFile()) {
                LOGGER.warning("Source file not found: " + sourceFile.getAbsolutePath());
                return buildErrorResponse(Response.Status.NOT_FOUND, MSG_FILE_NOT_FOUND);
            }

            // Create temporary file with access token
            TemporaryAccessToken tokenInfo = createTemporaryFile(sourceFile, sanitizedFileName);
            
            // Schedule cleanup of expired files
            scheduleCleanup();
            
            LOGGER.info("Temporary access token created successfully:");
            LOGGER.info("- Token: " + tokenInfo.token);
            LOGGER.info("- Temp file: " + tokenInfo.tempFile.getAbsolutePath());
            LOGGER.info("========= AccessToDocumentV2 - End Processing =========");

            return buildSuccessResponse(tokenInfo.token, tokenInfo.tempFile.getName());

        } catch (Exception e) {
            LOGGER.severe("Exception during temporary access creation: " + e.getMessage());
            return buildErrorResponse(Response.Status.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    // ============================================
    // PRIVATE HELPER METHODS
    // ============================================

    /**
     * Logs all request headers for debugging purposes
     */
    private void logRequestHeaders(String documentId, String fileName, String documentType,
                                   String applicationId, String sessionId) {
        LOGGER.info(HEADER_DOC_ID + "     = " + documentId);
        LOGGER.info(HEADER_FILE_NAME + "  = " + fileName);
        LOGGER.info(HEADER_DOC_TYPE + "  = " + documentType);
        LOGGER.info(HEADER_APP_ID + "    = " + applicationId);
        LOGGER.info(HEADER_SESSION_ID + "= " + sessionId);
    }

    /**
     * Validates all required request headers
     */
    private boolean validateHeaders(String documentId, String fileName, String documentType,
                                    String applicationId, String sessionId) {
        return documentId != null && !documentId.trim().isEmpty() &&
               fileName != null && !fileName.trim().isEmpty() &&
               documentType != null && !documentType.trim().isEmpty() &&
               applicationId != null && !applicationId.trim().isEmpty() &&
               sessionId != null && !sessionId.trim().isEmpty();
    }

    /**
     * Sanitizes filename to prevent path traversal attacks
     */
    private String sanitizeFileName(String fileName) {
        // Remove any path components and sanitize
        String baseName = new File(fileName).getName();
        return baseName.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    /**
     * Validates APEX session using ORDS service
     */
    private boolean isApexSessionValid(String applicationId, String sessionId) {
        if (ORDS_VALIDATE_URL == null || ORDS_VALIDATE_URL.isEmpty()) {
            LOGGER.severe("ORDS validation URL is not configured");
            return false;
        }

        try {
            URL url = new URL(ORDS_VALIDATE_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            // Configure connection
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty(HEADER_APP_ID, applicationId);
            connection.setRequestProperty(HEADER_SESSION_ID, sessionId);

            // Read response
            int statusCode = connection.getResponseCode();
            String responseBody = readResponse(connection, statusCode);
            
            LOGGER.info("ORDS validation response: " + responseBody);

            // Check if session is valid
            return responseBody.contains("\"is_valid\":\"1\"") || 
                   responseBody.contains("\"is_valid\":1");

        } catch (Exception e) {
            LOGGER.severe("APEX session validation error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Reads response from HTTP connection
     */
    private String readResponse(HttpURLConnection connection, int statusCode) throws Exception {
        InputStream inputStream = (statusCode >= 200 && statusCode < 300) ? 
                                 connection.getInputStream() : connection.getErrorStream();
        
        if (inputStream == null) {
            return "";
        }

        StringBuilder responseBuilder = new StringBuilder();
        byte[] buffer = new byte[1024];
        int bytesRead;
        
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            responseBuilder.append(new String(buffer, 0, bytesRead));
        }
        
        return responseBuilder.toString().trim();
    }

    /**
     * Creates a temporary copy of the source file with a unique token
     */
    private TemporaryAccessToken createTemporaryFile(File sourceFile, String fileName) throws Exception {
        // Ensure temp directory exists
        File tempDir = new File(TEMP_BASE_DIR);
        if (!tempDir.exists() && !tempDir.mkdirs()) {
            throw new RuntimeException("Failed to create temp directory: " + TEMP_BASE_DIR);
        }

        // Generate unique token
        String token = UUID.randomUUID().toString();
        
        // Create temp file with token prefix
        File tempFile = new File(tempDir, token + "_" + fileName);
        
        // Copy file using NIO for better performance
        NioPath sourcePath = sourceFile.toPath();
        NioPath targetPath = tempFile.toPath();
        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        
        // Set last modified time to now for expiration tracking
        tempFile.setLastModified(System.currentTimeMillis());
        
        return new TemporaryAccessToken(token, tempFile);
    }

    /**
     * Schedules cleanup of expired temporary files
     */
    private void scheduleCleanup() {
        CLEANUP_EXECUTOR.submit(() -> {
            try {
                cleanupExpiredTempFiles();
            } catch (Exception e) {
                LOGGER.warning("Error during temp file cleanup: " + e.getMessage());
            }
        });
    }

    /**
     * Cleans up expired temporary files
     */
    private static void cleanupExpiredTempFiles() {
        File tempDir = new File(TEMP_BASE_DIR);
        if (!tempDir.exists() || !tempDir.isDirectory()) {
            return;
        }

        File[] files = tempDir.listFiles();
        if (files == null) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        int deletedCount = 0;
        
        for (File file : files) {
            if (!file.isFile()) {
                continue;
            }

            long fileAge = currentTime - file.lastModified();
            if (fileAge > TEMP_FILE_EXPIRATION_MS) {
                if (file.delete()) {
                    deletedCount++;
                    LOGGER.info("Deleted expired temp file: " + file.getName());
                } else {
                    LOGGER.warning("Failed to delete temp file: " + file.getName());
                }
            }
        }
        
        if (deletedCount > 0) {
            LOGGER.info("Cleanup completed. Deleted " + deletedCount + " expired files.");
        }
    }

    /**
     * Builds a success JSON response with token information
     */
    private Response buildSuccessResponse(String token, String tempFileName) {
        String jsonResponse = String.format(
            "{\"%s\":\"%s\",\"%s\":\"%s\",\"%s\":\"%s\"}",
            JSON_KEY_STATUS, MSG_SUCCESS,
            JSON_KEY_TOKEN, token,
            JSON_KEY_FILE, tempFileName
        );
        
        return Response.ok(jsonResponse)
                .header("Access-Control-Allow-Origin", "*")
                .build();
    }

    /**
     * Builds an error JSON response
     */
    private Response buildErrorResponse(Response.Status status, String errorMessage) {
        String jsonResponse = String.format(
            "{\"%s\":\"%s\"}",
            JSON_KEY_ERROR, escapeJsonString(errorMessage)
        );
        
        return Response.status(status)
                .entity(jsonResponse)
                .header("Access-Control-Allow-Origin", "*")
                .build();
    }

    /**
     * Escapes special characters in JSON strings
     */
    private String escapeJsonString(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\"", "\\\"")
                    .replace("\\", "\\\\")
                    .replace("\b", "\\b")
                    .replace("\f", "\\f")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }

    // ============================================
    // HELPER CLASS FOR TEMP FILE INFO
    // ============================================

    /**
     * Helper class to hold temporary access token information
     */
    private static class TemporaryAccessToken {
        final String token;
        final File tempFile;
        
        TemporaryAccessToken(String token, File tempFile) {
            this.token = token;
            this.tempFile = tempFile;
        }
    }
}