package com.legacy.cm;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Legacy J2EE Java Servlet showcasing:
 * 1. Servlet Lifecycle (init, service, doGet/doPost, destroy)
 * 2. Legacy Direct Db2 SQL query handling (Before Migration)
 * 3. Modern Spring Boot REST API consumption with OAuth2 and InputStream/OutputStream binary streaming (After Migration)
 */
public class LegacyContentManagerServlet extends HttpServlet {

    private static final Logger LOGGER = Logger.getLogger(LegacyContentManagerServlet.class.getName());

    private LegacyDirectDb2Dao legacyDao;
    private ModernContentRestClient restClient;
    private String defaultItemId;

    // =========================================================================
    // 1. SERVLET LIFECYCLE: init()
    // =========================================================================
    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);
        LOGGER.info("====================================================================");
        LOGGER.info("[Servlet Lifecycle] init() invoked for LegacyContentManagerServlet");
        LOGGER.info("====================================================================");

        // Read servlet init-param
        this.defaultItemId = config.getInitParameter("defaultItemId");
        if (this.defaultItemId == null) {
            this.defaultItemId = "ICM_ITEM_001";
        }

        // Read context-param or environment variable overrides
        String restUrl = System.getenv("TARGET_REST_API_URL");
        if (restUrl == null || restUrl.isBlank()) {
            restUrl = config.getServletContext().getInitParameter("targetRestApiUrl");
        }
        if (restUrl == null || restUrl.isBlank()) {
            restUrl = "http://ms2-service:8082";
        }

        String clientId = System.getenv("OAUTH_CLIENT_ID");
        if (clientId == null || clientId.isBlank()) {
            clientId = config.getServletContext().getInitParameter("oauthClientId");
        }
        if (clientId == null || clientId.isBlank()) {
            clientId = "legacy-app";
        }

        String clientSecret = System.getenv("OAUTH_CLIENT_SECRET");
        if (clientSecret == null || clientSecret.isBlank()) {
            clientSecret = config.getServletContext().getInitParameter("oauthClientSecret");
        }
        if (clientSecret == null || clientSecret.isBlank()) {
            clientSecret = "cm-secret-123";
        }

        String dbUrl = System.getenv("DB_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = config.getServletContext().getInitParameter("dbUrl");
        }
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = "jdbc:postgresql://postgres-db:5432/dockerdemo";
        }

        String dbUser = System.getenv("DB_USER");
        if (dbUser == null || dbUser.isBlank()) {
            dbUser = config.getServletContext().getInitParameter("dbUser");
        }
        if (dbUser == null || dbUser.isBlank()) {
            dbUser = "admin";
        }

        String dbPassword = System.getenv("DB_PASSWORD");
        if (dbPassword == null || dbPassword.isBlank()) {
            dbPassword = config.getServletContext().getInitParameter("dbPassword");
        }
        if (dbPassword == null || dbPassword.isBlank()) {
            dbPassword = "admin123";
        }

        LOGGER.info("[Servlet Config] Target REST API: " + restUrl);
        LOGGER.info("[Servlet Config] Db2 / Database URL: " + dbUrl);

        this.legacyDao = new LegacyDirectDb2Dao(dbUrl, dbUser, dbPassword);
        this.restClient = new ModernContentRestClient(restUrl, clientId, clientSecret);

        LOGGER.info("[Servlet Lifecycle] init() completed successfully.");
    }

    // =========================================================================
    // 2. SERVLET LIFECYCLE: service()
    // =========================================================================
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        LOGGER.info("[Servlet Lifecycle] service() handling request: " + req.getMethod() + " " + req.getRequestURI());
        super.service(req, resp);
    }

    // =========================================================================
    // 3. SERVLET REQUEST HANDLING: doGet()
    // =========================================================================
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String mode = req.getParameter("mode");
        if (mode == null || mode.isBlank()) {
            mode = "sql"; // Default to legacy direct SQL
        }

        String itemId = req.getParameter("itemId");
        if (itemId == null || itemId.isBlank()) {
            itemId = defaultItemId;
        }

        boolean download = "true".equalsIgnoreCase(req.getParameter("download"));

        switch (mode.toLowerCase()) {
            case "sql":
                handleLegacyDb2SqlQuery(itemId, download, req, resp);
                break;
            case "rest":
                handleModernRestConsumption(req, resp);
                break;
            case "gap":
                handleMigrationGapAnalysis(itemId, resp);
                break;
            default:
                resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unknown mode: " + mode + ". Use 'sql', 'rest', or 'gap'.");
        }
    }

    /**
     * MODE A: Legacy Direct Db2 SQL Query (Before Migration)
     */
    private void handleLegacyDb2SqlQuery(String itemId, boolean download, HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        LOGGER.info("[Mode: Legacy Db2 SQL] Processing request for itemId=" + itemId);

        try {
            LegacyDocumentRecord doc = legacyDao.findDocumentByItemId(itemId);

            if (doc == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                resp.setContentType("application/json");
                resp.getWriter().write("{\"error\": \"Document not found for ItemID: " + itemId + "\"}");
                return;
            }

            if (download && doc.getDocumentData() != null) {
                // Stream binary payload directly to client OutputStream
                resp.setContentType(doc.getMimeType());
                resp.setHeader("Content-Disposition", "attachment; filename=\"" + doc.getFileName() + "\"");
                resp.setContentLengthLong(doc.getDocSize());

                try (OutputStream os = resp.getOutputStream()) {
                    os.write(doc.getDocumentData());
                    os.flush();
                }
            } else {
                // Return structured JSON describing the query and retrieved data
                resp.setContentType("application/json");
                PrintWriter out = resp.getWriter();
                out.write("{\n");
                out.write("  \"executionMode\": \"LEGACY_DIRECT_DB2_SQL\",\n");
                out.write("  \"status\": \"SUCCESS\",\n");
                out.write("  \"executedSql\": \"" + LegacyDirectDb2Dao.LEGACY_DB2_SQL.replace("\n", " ").replace("\"", "\\\"") + "\",\n");
                out.write("  \"retrievedItem\": {\n");
                out.write("    \"itemId\": \"" + doc.getItemId() + "\",\n");
                out.write("    \"versionId\": " + doc.getVersionId() + ",\n");
                out.write("    \"itemTypeName\": \"" + doc.getItemTypeName() + "\",\n");
                out.write("    \"createdTimestamp\": \"" + doc.getCreatedTimestamp() + "\",\n");
                out.write("    \"aclCode\": \"" + doc.getAclCode() + "\",\n");
                out.write("    \"fileName\": \"" + doc.getFileName() + "\",\n");
                out.write("    \"mimeType\": \"" + doc.getMimeType() + "\",\n");
                out.write("    \"docSize\": " + doc.getDocSize() + ",\n");
                out.write("    \"hasBinaryData\": " + (doc.getDocumentData() != null) + "\n");
                out.write("  },\n");
                out.write("  \"downloadBinaryUrl\": \"/legacy-cm-web/legacy-cm?mode=sql&download=true&itemId=" + itemId + "\"\n");
                out.write("}\n");
            }

        } catch (SQLException e) {
            LOGGER.log(Level.SEVERE, "SQL Execution failed: " + e.getMessage(), e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\": \"Database error: " + e.getMessage() + "\"}");
        }
    }

    /**
     * MODE B: Migrated Modern REST API Consumption with OAuth2 & Streaming (After Migration)
     */
    private void handleModernRestConsumption(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String docIdParam = req.getParameter("docId");
        long docId = 1L;
        if (docIdParam != null && !docIdParam.isBlank()) {
            try {
                docId = Long.parseLong(docIdParam);
            } catch (NumberFormatException ignored) {}
        }

        boolean download = "true".equalsIgnoreCase(req.getParameter("download"));
        LOGGER.info("[Mode: Modern REST] Consuming target Spring Boot API for docId=" + docId);

        try {
            if (download) {
                // Stream binary content from REST API directly to client OutputStream
                resp.setContentType("application/octet-stream");
                resp.setHeader("Content-Disposition", "attachment; filename=\"rest_streamed_doc_" + docId + ".pdf\"");

                try (OutputStream clientOut = resp.getOutputStream()) {
                    boolean success = restClient.streamDocument(docId, clientOut);
                    if (!success) {
                        resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Document ID not found in target REST API");
                    }
                }
            } else {
                // Return metadata retrieved via REST
                String jsonMetadata = restClient.getDocumentMetadata(docId);
                String token = restClient.getAccessToken();

                resp.setContentType("application/json");
                PrintWriter out = resp.getWriter();
                out.write("{\n");
                out.write("  \"executionMode\": \"MIGRATED_SPRING_BOOT_REST_API\",\n");
                out.write("  \"oauth2Authentication\": {\n");
                out.write("    \"authType\": \"OAuth2 Client-Credentials Flow\",\n");
                out.write("    \"bearerTokenSample\": \"" + token.substring(0, Math.min(20, token.length())) + "...\",\n");
                out.write("    \"tokenScope\": \"documents:read documents:write\"\n");
                out.write("  },\n");
                out.write("  \"targetRestMetadata\": " + jsonMetadata + ",\n");
                out.write("  \"downloadBinaryStreamUrl\": \"/legacy-cm-web/legacy-cm?mode=rest&download=true&docId=" + docId + "\"\n");
                out.write("}\n");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "REST call interrupted");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "REST call failed: " + e.getMessage(), e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"error\": \"REST API error: " + e.getMessage() + "\"}");
        }
    }

    /**
     * MODE C: Migration Gap Analysis (Discovery -> Gap Analysis -> Modern REST)
     */
    private void handleMigrationGapAnalysis(String itemId, HttpServletResponse resp) throws IOException {
        resp.setContentType("application/json");
        PrintWriter out = resp.getWriter();
        out.write("""
                {
                  "migrationTitle": "IBM Content Manager to Spring Boot REST API Migration",
                  "discoveryPhase": {
                    "legacyMechanism": "Direct SQL join against Db2 catalog tables (ICMSTITEMS001001, ICMSTITEMTYPEDEFS, ICMSTCOLLNAME001001)",
                    "legacySdk": "DKDatastoreICM, DKDDO, DKLobICM (Heavy native C++ JNI bridge)",
                    "issues": [
                      "Tight coupling to Db2 internal storage layout",
                      "Lack of central security / OAuth2 authorization",
                      "High memory consumption loading whole LOBs into heap",
                      "Zero API reusability for mobile or modern web applications"
                    ]
                  },
                  "gapAnalysis": [
                    {
                      "legacyDb2Column": "itm.ITEMID",
                      "targetRestField": "itemId",
                      "notes": "Preserved for backward-compatible document lookups"
                    },
                    {
                      "legacyDb2Column": "typ.ITEMTYPENAME",
                      "targetRestField": "itemType",
                      "notes": "Converted from relational FK lookup to explicit enum/string"
                    },
                    {
                      "legacyDb2Column": "doc.DOCUMENT_DATA (BLOB/BYTEA)",
                      "targetRestField": "GET /api/v1/documents/{id}/content",
                      "notes": "Migrated from direct JDBC ResultSet reading to HTTP chunked streaming (InputStream/OutputStream)"
                    },
                    {
                      "legacyDb2Column": "Direct Db2 DB credentials",
                      "targetRestField": "OAuth2 Client-Credentials (POST /oauth/token)",
                      "notes": "Secured with Bearer token authentication"
                    }
                  ],
                  "targetArchitecture": {
                    "framework": "Spring Boot 3.x REST API",
                    "documentation": "OpenAPI / Swagger UI (/swagger-ui.html)",
                    "streaming": "org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody",
                    "authentication": "OAuth2 Client Credentials (Bearer Token)"
                  }
                }
                """);
    }

    // =========================================================================
    // 4. SERVLET LIFECYCLE: destroy()
    // =========================================================================
    @Override
    public void destroy() {
        LOGGER.info("====================================================================");
        LOGGER.info("[Servlet Lifecycle] destroy() invoked - releasing DAO & REST resources");
        LOGGER.info("====================================================================");
        super.destroy();
    }
}
