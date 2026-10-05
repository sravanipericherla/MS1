package com.legacy.cm;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Servlet handling multipart document uploads and forwarding the binary stream
 * to the modern Spring Boot REST API using OAuth2 Bearer token authentication.
 */
@MultipartConfig(
        fileSizeThreshold = 1024 * 1024,      // 1 MB
        maxFileSize = 1024 * 1024 * 50,       // 50 MB
        maxRequestSize = 1024 * 1024 * 50     // 50 MB
)
public class LegacyDocumentUploadServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(LegacyDocumentUploadServlet.class.getName());

    private ModernContentRestClient restClient;

    @Override
    public void init() throws ServletException {
        super.init();
        String restUrl = System.getenv("TARGET_REST_API_URL");
        if (restUrl == null || restUrl.isBlank()) {
            restUrl = getServletContext().getInitParameter("targetRestApiUrl");
        }
        if (restUrl == null || restUrl.isBlank()) {
            restUrl = "http://ms2-service:8082";
        }

        String clientId = System.getenv("OAUTH_CLIENT_ID");
        if (clientId == null || clientId.isBlank()) {
            clientId = getServletContext().getInitParameter("oauthClientId");
        }
        if (clientId == null || clientId.isBlank()) {
            clientId = "legacy-app";
        }

        String clientSecret = System.getenv("OAUTH_CLIENT_SECRET");
        if (clientSecret == null || clientSecret.isBlank()) {
            clientSecret = getServletContext().getInitParameter("oauthClientSecret");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            clientSecret = "cm-secret-123";
        }

        this.restClient = new ModernContentRestClient(restUrl, clientId, clientSecret);
        LOGGER.info("[Upload Servlet] Initialized with Target REST API: " + restUrl);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        LOGGER.info("[Upload Servlet] Processing multipart upload request...");

        Part filePart = req.getPart("file");
        if (filePart == null || filePart.getSize() == 0) {
            resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\": \"No file uploaded or file part is missing.\"}");
            return;
        }

        String fileName = filePart.getSubmittedFileName();
        if (fileName == null || fileName.isBlank()) {
            fileName = "uploaded_document_" + System.currentTimeMillis();
        }

        String contentType = filePart.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }

        String itemType = req.getParameter("itemType");
        if (itemType == null || itemType.isBlank()) {
            itemType = "GENERAL_DOCUMENT";
        }

        LOGGER.info("[Upload Servlet] Forwarding file: '" + fileName + "' (" + filePart.getSize() + " bytes), mimeType=" + contentType);

        // Read file part using InputStream into memory buffer
        byte[] fileBytes;
        try (InputStream is = filePart.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] chunk = new byte[8192];
            int read;
            while ((read = is.read(chunk)) != -1) {
                baos.write(chunk, 0, read);
            }
            fileBytes = baos.toByteArray();
        }

        try {
            // Forward to Spring Boot REST API
            String restApiResponse = restClient.uploadDocument(fileName, contentType, fileBytes, itemType);

            resp.setStatus(HttpServletResponse.SC_CREATED);
            resp.setContentType("application/json");
            PrintWriter out = resp.getWriter();
            out.write("{\n");
            out.write("  \"message\": \"Document successfully received by Servlet and forwarded to Spring Boot REST API!\",\n");
            out.write("  \"springBootResponse\": " + restApiResponse + "\n");
            out.write("}\n");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to upload to target REST API: " + e.getMessage(), e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\": \"Error forwarding upload to REST API: " + e.getMessage() + "\"}");
        }
    }
}
