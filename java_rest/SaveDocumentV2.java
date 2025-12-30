package tokenrest;

/**
 * REST endpoint for saving documents with APEX session validation
 * @author Saeed Ahmadzad-Asl
 */

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
import java.util.Properties;
import java.util.logging.Logger;

@Path("/SaveDocumentV2")
public class SaveDocumentV2 {

    // ============================================
    // CONSTANTS & CONFIGURATION
    // ============================================

    private static final Logger LOGGER = Logger.getLogger(SaveDocumentV2.class.getName());
    private static final String PATH_CONFIG_FILE = "/u01/oracle/config/document-paths.properties";
    private static final Properties DOCUMENT_PATHS = new Properties();
    
    // Header constants
    private static final String HEADER_DOC_ID = "X-Doc-Id";
    private static final String HEADER_FILE_NAME = "X-File-Name";
    private static final String HEADER_DOC_TYPE = "X-Doc-Type";
    private static final String HEADER_APP_ID = "p_app_id";
    private static final String HEADER_SESSION_ID = "p_session_id";
    
    // Response messages
    private static final String MSG_MISSING_HEADERS = "Missing required headers";
    private static final String MSG_INVALID_SESSION = "Invalid or expired APEX session";
    private static final String MSG_INVALID_DOC_TYPE = "Invalid document type";
    private static final String MSG_SUCCESS = "OK";
    
    // JSON keys
    private static final String JSON_KEY_ERROR = "error";
    private static final String JSON_KEY_STATUS = "status";
    private static final String JSON_KEY_FILE = "file";

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

            LOGGER.info("Document paths loaded successfully. Entries: " + DOCUMENT_PATHS.size());
            
        } catch (Exception e) {
            LOGGER.severe("Failed to load configuration file: " + PATH_CONFIG_FILE + 
                         " - Error: " + e.getMessage());
        }
    }

    // ============================================
    // CORS PREFLIGHT HANDLER
    // ============================================

    @OPTIONS
    @Path("/SaveDocumentV2")
    public Response handleCorsPreflight() {
        return Response.ok()
                .header("Access-Control-Allow-Origin", "*")
                .header("Access-Control-Allow-Methods", "POST, OPTIONS")
                .header("Access-Control-Allow-Headers", 
                        "X-Doc-Id, X-File-Name, X-Doc-Type, p_app_id, p_session_id, Content-Type")
                .build();
    }

    // ============================================
    // MAIN ENDPOINT - FILE UPLOAD
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

        LOGGER.info("========= SaveDocumentV2 - Start Processing =========");
        logRequestHeaders(documentId, fileName, documentType, applicationId, sessionId);

        try {
            // Validate required headers
            Response validationResponse = validateHeaders(fileStream, documentId, fileName, 
                                                         documentType, applicationId, sessionId);
            if (validationResponse != null) {
                return validationResponse;
            }

            // Validate APEX session
            if (!isApexSessionValid(applicationId, sessionId)) {
                LOGGER.warning("APEX session validation failed for appId: " + applicationId);
                return buildErrorResponse(Response.Status.UNAUTHORIZED, MSG_INVALID_SESSION);
            }

            LOGGER.info("APEX session validated successfully");

            // Resolve storage path
            String basePath = resolveBasePath(documentType);
            if (basePath == null) {
                LOGGER.warning("No path configuration found for document type: " + documentType);
                return buildErrorResponse(Response.Status.BAD_REQUEST, MSG_INVALID_DOC_TYPE);
            }

            // Save file to disk
            File savedFile = saveFileToDisk(fileStream, documentId, fileName, basePath);
            
            LOGGER.info("File saved successfully: " + savedFile.getAbsolutePath());
            LOGGER.info("========= SaveDocumentV2 - End Processing =========");

            return buildSuccessResponse(savedFile.getName());

        } catch (Exception e) {
            LOGGER.severe("Exception during file save operation: " + e.getMessage());
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
    private Response validateHeaders(InputStream fileStream, String documentId, String fileName,
                                     String documentType, String applicationId, String sessionId) {
        
        if (fileStream == null || documentId == null || fileName == null || 
            documentType == null || applicationId == null || sessionId == null) {
            
            LOGGER.warning("Missing one or more required headers");
            return buildErrorResponse(Response.Status.BAD_REQUEST, MSG_MISSING_HEADERS);
        }
        
        return null; // All headers are valid
    }

    /**
     * Validates APEX session using ORDS service
     */
    private boolean isApexSessionValid(String applicationId, String sessionId) {
        String validationUrl = DOCUMENT_PATHS.getProperty("ORDS_VALIDATE_SESSION");
        
        if (validationUrl == null || validationUrl.isEmpty()) {
            LOGGER.severe("ORDS validation URL is not configured");
            return false;
        }

        try {
            URL url = new URL(validationUrl);
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
     * Resolves storage path based on document type
     */
    private String resolveBasePath(String documentType) {
        String path = DOCUMENT_PATHS.getProperty(documentType);
        
        if (path != null) {
            LOGGER.info("Resolved path for document type '" + documentType + "': " + path);
        } else {
            LOGGER.warning("No path configuration found for document type: " + documentType);
        }
        
        return path;
    }

    /**
     * Saves file to disk
     */
    private File saveFileToDisk(InputStream inputStream, String documentId, 
                               String fileName, String basePath) throws Exception {
        
        // Sanitize filename
        String sanitizedFileName = fileName.replaceAll("[\\/:*?\"<>|]", "_");
        
        // Ensure directory exists
        File directory = new File(basePath);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new RuntimeException("Failed to create directory: " + basePath);
        }

        // Create target file
        File targetFile = new File(directory, documentId + "_" + sanitizedFileName);
        
        // Write file with buffered stream
        try (OutputStream outputStream = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;
            
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }
            
            LOGGER.info("File written successfully. Size: " + totalBytes + " bytes");
        }
        
        return targetFile;
    }

    /**
     * Builds a success JSON response
     */
    private Response buildSuccessResponse(String fileName) {
        String jsonResponse = String.format(
            "{\"%s\":\"%s\",\"%s\":\"%s\"}",
            JSON_KEY_STATUS, MSG_SUCCESS,
            JSON_KEY_FILE, fileName
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
            JSON_KEY_ERROR, errorMessage.replace("\"", "\\"")
        );
        
        return Response.status(status)
                .entity(jsonResponse)
                .header("Access-Control-Allow-Origin", "*")
                .build();
    }
}
