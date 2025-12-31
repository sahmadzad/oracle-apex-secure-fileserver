package tokenrest;

import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.OPTIONS;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * REST endpoint for saving uploaded documents with APEX session validation.
 * 
 * @author Saeed Ahmadzad-Asl
 */
@Path("/SaveDocumentV2")
public class SaveDocumentV2 {
    
    // ============================================
    // CONSTANTS AND CONFIGURATION
    // ============================================
    
    private static final Logger LOGGER = Logger.getLogger(SaveDocumentV2.class.getName());
    private static final String PATH_CONFIG_FILE = "/u01/oracle/config/document-paths.properties";
    private static final Properties DOCUMENT_PATHS = new Properties();
    
    // Response messages
    private static final String RESPONSE_OK = "{\"status\":\"OK\",\"file\":\"%s\"}";
    private static final String ERROR_MISSING_HEADERS = "{\"error\":\"Missing required headers\"}";
    private static final String ERROR_INVALID_SESSION = "{\"error\":\"Invalid or expired APEX session\"}";
    private static final String ERROR_INVALID_DOCTYPE = "{\"error\":\"Invalid document type\"}";
    private static final String ERROR_TEMPLATE = "{\"error\":\"%s\"}";
    
    // HTTP header constants
    private static final String HEADER_DOC_ID = "X-Doc-Id";
    private static final String HEADER_FILE_NAME = "X-File-Name";
    private static final String HEADER_DOC_TYPE = "X-Doc-Type";
    private static final String HEADER_APP_ID = "p_app_id";
    private static final String HEADER_SESSION_ID = "p_session_id";
    
    // Buffer size for file operations
    private static final int BUFFER_SIZE = 8192;
    private static final int CONNECTION_TIMEOUT = 50000;
    private static final int READ_TIMEOUT = 50000;
    
    // ============================================
    // STATIC INITIALIZATION
    // ============================================
    
    static {
        loadDocumentPaths();
    }
    
    /**
     * Loads document paths configuration from properties file.
     */
    private static void loadDocumentPaths() {
        try {
            File configFile = new File(PATH_CONFIG_FILE);
            LOGGER.info("Loading document paths from: " + configFile.getAbsolutePath());
            
            try (InputStream inputStream = new FileInputStream(configFile)) {
                DOCUMENT_PATHS.load(inputStream);
                LOGGER.info("Document paths loaded successfully: " + DOCUMENT_PATHS);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load configuration file: " + PATH_CONFIG_FILE, e);
        }
    }
    
    // ============================================
    // CORS PREFLIGHT HANDLER
    // ============================================
    
    @OPTIONS
    public Response handleCorsPreflight() {
        return Response.ok().build();
    }
    
    // ============================================
    // MAIN DOCUMENT SAVE ENDPOINT
    // ============================================
    
    @POST
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    @Produces(MediaType.APPLICATION_JSON)
    public Response saveDocument(
            InputStream fileStream,
            @HeaderParam(HEADER_DOC_ID) String documentId,
            @HeaderParam(HEADER_FILE_NAME) String fileName,
            @HeaderParam(HEADER_DOC_TYPE) String documentType,
            @HeaderParam(HEADER_APP_ID) String applicationId,
            @HeaderParam(HEADER_SESSION_ID) String sessionId) {
        
        LOGGER.info("========= SaveDocumentV2 START =========");
        
        try {
            // Log incoming request
            logRequestHeaders(documentId, fileName, documentType, applicationId, sessionId);
            
            // Validate required headers
            if (!validateHeaders(fileStream, documentId, fileName, documentType, applicationId, sessionId)) {
                return buildBadRequestResponse(ERROR_MISSING_HEADERS);
            }
            
            LOGGER.info("✅ Headers validated — checking APEX session");
            
            // Validate APEX session
            if (!isApexSessionValid(applicationId, documentId, sessionId)) {
                LOGGER.warning("❌ APEX session validation failed");
                return buildUnauthorizedResponse(ERROR_INVALID_SESSION);
            }
            
            LOGGER.info("✅ APEX session validated successfully");
            
            // Resolve storage path
            String basePath = resolveBasePath(documentType);
            if (basePath == null) {
                return buildBadRequestResponse(ERROR_INVALID_DOCTYPE);
            }
            
            LOGGER.info("Resolved storage path: " + basePath);
            
            // Save the file
            File savedFile = saveFileToDisk(fileStream, documentId, fileName, basePath);
            
            LOGGER.info("✅ File saved successfully: " + savedFile.getAbsolutePath());
            LOGGER.info("========= SaveDocumentV2 END =========");
            
            return buildSuccessResponse(savedFile.getName());
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Document save operation failed", e);
            return buildServerErrorResponse(e.getMessage());
        }
    }
    
    // ============================================
    // VALIDATION METHODS
    // ============================================
    
    /**
     * Validates that all required headers are present.
     */
    private boolean validateHeaders(
            InputStream fileStream,
            String documentId,
            String fileName,
            String documentType,
            String applicationId,
            String sessionId) {
        
        return fileStream != null &&
               documentId != null && !documentId.trim().isEmpty() &&
               fileName != null && !fileName.trim().isEmpty() &&
               documentType != null && !documentType.trim().isEmpty() &&
               applicationId != null && !applicationId.trim().isEmpty() &&
               sessionId != null && !sessionId.trim().isEmpty();
    }
    
    /**
     * Validates APEX session by calling ORDS endpoint.
     */
    private boolean isApexSessionValid(String applicationId, String documentId, String sessionId) {
        String ordsEndpoint = DOCUMENT_PATHS.getProperty("ORDS_VALIDATE_SESSION");
        
        if (ordsEndpoint == null || ordsEndpoint.trim().isEmpty()) {
            LOGGER.severe("ORDS validation endpoint not configured");
            return false;
        }
        
        try {
            URL url = new URL(ordsEndpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            
            configureConnection(connection);
            addSessionValidationHeaders(connection, applicationId, documentId, sessionId);
            
            int responseCode = connection.getResponseCode();
            String responseBody = readResponse(connection, responseCode);
            
            LOGGER.info("ORDS validation response: " + responseBody);
            
            return isSessionValidResponse(responseBody);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "APEX session validation error", e);
            return false;
        }
    }
    
    /**
     * Configures HTTP connection for ORDS validation.
     */
    private void configureConnection(HttpURLConnection connection) throws Exception {
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(CONNECTION_TIMEOUT);
        connection.setReadTimeout(READ_TIMEOUT);
        connection.setRequestProperty("Accept", "application/json");
    }
    
    /**
     * Adds session validation headers to the HTTP connection.
     */
    private void addSessionValidationHeaders(
            HttpURLConnection connection,
            String applicationId,
            String documentId,
            String sessionId) {
        
        connection.setRequestProperty(HEADER_APP_ID, applicationId);
        connection.setRequestProperty(HEADER_SESSION_ID, sessionId);
        connection.setRequestProperty("p_emp_no", documentId);
    }
    
    /**
     * Reads response from HTTP connection.
     */
    private String readResponse(HttpURLConnection connection, int responseCode) throws Exception {
        try (InputStream inputStream = responseCode >= 200 && responseCode < 300 ? 
                connection.getInputStream() : connection.getErrorStream()) {
            
            if (inputStream == null) {
                return "";
            }
            
            byte[] buffer = new byte[BUFFER_SIZE];
            StringBuilder responseBuilder = new StringBuilder();
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                responseBuilder.append(new String(buffer, 0, bytesRead, StandardCharsets.UTF_8));
            }
            
            return responseBuilder.toString().trim();
        }
    }
    
    /**
     * Checks if ORDS response indicates a valid session.
     */
    private boolean isSessionValidResponse(String jsonResponse) {
        return jsonResponse.contains("\"is_valid\":\"1\"") || 
               jsonResponse.contains("\"is_valid\":1");
    }
    
    // ============================================
    // PATH RESOLUTION
    // ============================================
    
    /**
     * Resolves storage path based on document type.
     */
    private String resolveBasePath(String documentType) {
        String path = DOCUMENT_PATHS.getProperty(documentType);
        
        if (path == null) {
            LOGGER.warning("No path configured for document type: " + documentType);
        } else {
            LOGGER.info("Resolved path for " + documentType + " → " + path);
        }
        
        return path;
    }
    
    // ============================================
    // FILE OPERATIONS
    // ============================================
    
    /**
     * Saves uploaded file to disk.
     */
    private File saveFileToDisk(
            InputStream inputStream,
            String documentId,
            String originalFileName,
            String basePath) throws Exception {
        
        // Sanitize file name
        String safeFileName = sanitizeFileName(originalFileName);
        String targetFileName = documentId + "_" + safeFileName;
        
        // Ensure directory exists
        File storageDirectory = new File(basePath);
        if (!storageDirectory.exists() && !storageDirectory.mkdirs()) {
            throw new RuntimeException("Failed to create directory: " + basePath);
        }
        
        // Save file
        File targetFile = new File(storageDirectory, targetFileName);
        writeStreamToFile(inputStream, targetFile);
        
        return targetFile;
    }
    
    /**
     * Sanitizes file name to prevent path traversal attacks.
     */
    private String sanitizeFileName(String fileName) {
        return fileName.replaceAll("[\\/:*?\"<>|]", "_");
    }
    
    /**
     * Writes input stream to file.
     */
    private void writeStreamToFile(InputStream inputStream, File targetFile) throws Exception {
        try (OutputStream outputStream = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }
    
    // ============================================
    // RESPONSE BUILDERS
    // ============================================
    
    private Response buildSuccessResponse(String fileName) {
        return Response.ok(String.format(RESPONSE_OK, fileName)).build();
    }
    
    private Response buildBadRequestResponse(String errorMessage) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(errorMessage)
                .build();
    }
    
    private Response buildUnauthorizedResponse(String errorMessage) {
        return Response.status(Response.Status.UNAUTHORIZED)
                .entity(errorMessage)
                .build();
    }
    
    private Response buildServerErrorResponse(String errorDetails) {
        return Response.serverError()
                .entity(String.format(ERROR_TEMPLATE, errorDetails))
                .build();
    }
    
    // ============================================
    // UTILITY METHODS
    // ============================================
    
    /**
     * Logs request headers for debugging.
     */
    private void logRequestHeaders(
            String documentId,
            String fileName,
            String documentType,
            String applicationId,
            String sessionId) {
        
        LOGGER.info(HEADER_DOC_ID + "     = " + documentId);
        LOGGER.info(HEADER_FILE_NAME + "  = " + fileName);
        LOGGER.info(HEADER_DOC_TYPE + "  = " + documentType);
        LOGGER.info(HEADER_APP_ID + "    = " + applicationId);
        LOGGER.info(HEADER_SESSION_ID + "= " + sessionId);
    }
}
