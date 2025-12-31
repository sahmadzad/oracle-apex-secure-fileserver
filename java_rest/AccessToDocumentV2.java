package tokenrest;
/**
 *
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
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.Properties;


@Path("/SaveDocumentV2")
public class SaveDocumentV2 {




private static final Logger logger =
        Logger.getLogger(SaveDocumentV2.class.getName());


private static final String PATH_FILE =
        "/u01/oracle/config/document-paths.properties";

private static final Properties PATHS = new Properties();

static {
    try {
        File f = new File(PATH_FILE);
        logger.info("Loading document paths from: " + f.getAbsolutePath());

        try (InputStream in = new FileInputStream(f)) {
            PATHS.load(in);
        }

        logger.info("Document paths loaded: " + PATHS);

    } catch (Exception e) {
        logger.severe("🔥 Cannot load " + PATH_FILE + " : " + e.getMessage());
    }
}



    /* =========================
       CORS PREFLIGHT (OPTIONS)
       ========================= */
    @OPTIONS
    public Response corsPreflight() {
        return Response.ok()
                .build();
    }

    /* =========================
       FILE UPLOAD (POST)
       ========================= */
@POST
@Consumes(MediaType.APPLICATION_OCTET_STREAM)
@Produces(MediaType.APPLICATION_JSON)
public Response saveDocument(
        InputStream body,
        @HeaderParam("X-Doc-Id") String docId,
        @HeaderParam("X-File-Name") String fileName,
        @HeaderParam("X-Doc-Type") String docType,
        @HeaderParam("p_app_id") String appId, //get app_CS to security_check
        @HeaderParam("p_session_id") String sessionId
) {
    try {
        logger.info("========= SaveDocumentV2 START =========");
        logger.info("X-Doc-Id     = " + docId);
        logger.info("X-File-Name  = " + fileName);
        logger.info("X-Doc-Type  = " + docType);
        logger.info("p_app_id    = " + appId);
        logger.info("p_session_id= " + sessionId);

        if (body == null || docId == null || fileName == null || docType == null
                || appId == null || sessionId == null) {

            logger.warning("❌ Missing headers");
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Missing required headers\"}")
                    .build();
        }

        logger.info("✅ Headers OK — validating APEX session");

        if (!isApexSessionValid(appId,docId, sessionId)) {
            logger.warning("❌ APEX session rejected");
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity("{\"error\":\"Invalid or expired APEX session\"}")
                    .build();
        }

        logger.info("✅ APEX session VALID");

        String basePath = resolveBasePath(docType);
        logger.info("Resolved basePath = " + basePath);

        if (basePath == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("{\"error\":\"Invalid document type\"}")
                    .build();
        }

        File saved = saveToFile(body, docId, fileName, basePath);

        logger.info("✅ File saved: " + saved.getAbsolutePath());
        logger.info("========= SaveDocumentV2 END =========");

        return Response.ok(
                "{\"status\":\"OK\",\"file\":\"" + saved.getName() + "\"}"
        ).build();

    } catch (Exception e) {
        logger.severe("🔥 EXCEPTION: " + e.getMessage());
        return Response.serverError()
                .entity("{\"error\":\"" + e.getMessage() + "\"}")
                .build();
    }
}


private static final String ORDS_VALIDATE_SESSION = PATHS.getProperty("ORDS_VALIDATE_SESSION");

private boolean isApexSessionValid(String appId,String docId, String sessionId) {
    try {
        URL url = new URL(ORDS_VALIDATE_SESSION);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(50000);
        conn.setReadTimeout(50000);

        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("p_app_id", appId); //Send app_cs mapped with p_app_id to security check
        logger.severe("🔥 p_app_id: " + appId);
        conn.setRequestProperty("p_session_id", sessionId);
        conn.setRequestProperty("p_emp_no", docId);
        logger.severe("🔥 p_emp_no: " + docId);
        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();

        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[1024];
        int len;
        while ((len = is.read(buf)) != -1) {
            sb.append(new String(buf, 0, len));
        }

        String json = sb.toString().trim();
        logger.info("ORDS response: " + json);

        return json.contains("\"is_valid\":\"1\"") || json.contains("\"is_valid\":1");
    } catch (Exception e) {
        logger.severe("🔥 APEX validation error: " + e.getMessage());
        return false;
    }
}
    /* =========================
       PATH RESOLVER
       ========================= */

private static String resolveBasePath(String docType) {
    String path = PATHS.getProperty(docType);

    if (path == null) {
        logger.warning("❌ No path configured for docType=" + docType);
    } else {
        logger.info("Resolved docType " + docType + " → " + path);
    }

    return path;
}


    /* =========================
       FILE WRITE LOGIC
       ========================= */
    private static File saveToFile(
            InputStream body,
            String docId,
            String fileName,
            String basePath
    ) throws Exception {

        fileName = fileName.replaceAll("[\\/]", "_");

        File dir = new File(basePath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("Cannot create directory: " + basePath);
        }

        File targetFile = new File(dir, docId + "_" + fileName);

        try (OutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = body.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }

        return targetFile;
    }
    
}

