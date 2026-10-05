<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>IBM Content Manager to Spring Boot REST Migration Demo</title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet">
    <style>
        body { background-color: #f8f9fa; font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; }
        .hero { background: linear-gradient(135deg, #0f2027, #203a43, #2c5364); color: white; padding: 2.5rem 1rem; border-radius: 0 0 1rem 1rem; margin-bottom: 2rem; }
        .card { border: none; border-radius: 0.75rem; box-shadow: 0 4px 12px rgba(0,0,0,0.06); transition: transform 0.2s; }
        .card:hover { transform: translateY(-2px); }
        .badge-legacy { background-color: #dc3545; }
        .badge-modern { background-color: #198754; }
        .code-box { background: #1e1e1e; color: #d4d4d4; padding: 1rem; border-radius: 0.5rem; font-family: 'Courier New', monospace; font-size: 0.85rem; max-height: 250px; overflow-y: auto; }
        .highlight { color: #4ec9b0; }
    </style>
</head>
<body>

<div class="hero text-center">
    <div class="container">
        <h1 class="fw-bold mb-2">Legacy Application Migration Demo</h1>
        <h4 class="fw-light opacity-75">IBM Content Manager (Db2 SQL / SDK) &rarr; Spring Boot REST APIs</h4>
        <div class="mt-3">
            <span class="badge bg-warning text-dark px-3 py-2 me-2">J2EE EAR Application (IBM WebSphere Liberty)</span>
            <span class="badge bg-success px-3 py-2 me-2">Spring Boot 3.5.0 Microservices</span>
            <span class="badge bg-info text-dark px-3 py-2">OAuth2 Client-Credentials</span>
        </div>
        <div class="mt-4">
            <a href="http://localhost:8082/swagger-ui.html" target="_blank" class="btn btn-outline-light btn-sm me-2 fw-semibold">
                &boxbox; Open MS2 Swagger UI (Port 8082)
            </a>
            <a href="http://localhost:8081/swagger-ui.html" target="_blank" class="btn btn-outline-light btn-sm fw-semibold">
                &boxbox; Open MS1 Client Swagger UI (Port 8081)
            </a>
        </div>
    </div>
</div>

<div class="container pb-5">

    <!-- Row 1: Old Way vs New Way -->
    <div class="row g-4 mb-4">

        <!-- Card 1: Before Migration -->
        <div class="col-md-6">
            <div class="card h-100 border-top border-4 border-danger">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-center mb-3">
                        <h5 class="card-title mb-0 text-danger fw-bold">1. Before: Legacy Direct Db2 SQL</h5>
                        <span class="badge badge-legacy">Legacy J2EE DAO</span>
                    </div>
                    <p class="text-muted small">
                        Legacy application joins IBM Content Manager catalog tables (<code>ICMSTITEMS001001</code>, <code>ICMSTITEMTYPEDEFS</code>, <code>ICMSTCOLLNAME001001</code>) directly in Db2 via raw JDBC.
                    </p>

                    <div class="code-box mb-3">
                        SELECT itm.ITEMID, typ.ITEMTYPENAME, doc.FILENAME, doc.DOCUMENT_DATA<br>
                        FROM <span class="highlight">ICMSTITEMS001001</span> itm<br>
                        JOIN <span class="highlight">ICMSTITEMTYPEDEFS</span> typ ON itm.ITEMTYPEID = typ.ITEMTYPEID<br>
                        JOIN <span class="highlight">ICMSTCOLLNAME001001</span> doc ON itm.ITEMID = doc.ITEMID<br>
                        WHERE itm.ITEMID = ?
                    </div>

                    <div class="d-flex gap-2">
                        <a href="<%= request.getContextPath() %>/legacy-cm?mode=sql&itemId=ICM_ITEM_001" target="_blank" class="btn btn-outline-danger btn-sm">
                            &check; Trace Db2 SQL (JSON)
                        </a>
                        <a href="<%= request.getContextPath() %>/legacy-cm?mode=sql&download=true&itemId=ICM_ITEM_001" class="btn btn-danger btn-sm">
                            &DownArrowBar; Download BLOB (Direct JDBC)
                        </a>
                    </div>
                </div>
            </div>
        </div>

        <!-- Card 2: After Migration -->
        <div class="col-md-6">
            <div class="card h-100 border-top border-4 border-success">
                <div class="card-body">
                    <div class="d-flex justify-content-between align-items-center mb-3">
                        <h5 class="card-title mb-0 text-success fw-bold">2. After: Modern Spring Boot REST API</h5>
                        <span class="badge badge-modern">OAuth2 + REST Stream</span>
                    </div>
                    <p class="text-muted small">
                        Legacy servlet is refactored to obtain an OAuth2 Bearer token (Client-Credentials flow) and stream documents via HTTP using <code>InputStream</code> &amp; <code>OutputStream</code> chunking.
                    </p>

                    <div class="code-box mb-3">
                        1. POST /oauth/token &rarr; <span class="highlight">Bearer cm_token_...</span><br>
                        2. GET /api/v1/documents/1/content<br>
                        &nbsp;&nbsp;&nbsp;Authorization: Bearer [token]<br>
                        &nbsp;&nbsp;&nbsp;Transfer-Encoding: chunked (StreamUtils.copy)
                    </div>

                    <div class="d-flex gap-2">
                        <a href="<%= request.getContextPath() %>/legacy-cm?mode=rest&docId=1" target="_blank" class="btn btn-outline-success btn-sm">
                            &check; Call REST via Servlet (JSON)
                        </a>
                        <a href="<%= request.getContextPath() %>/legacy-cm?mode=rest&download=true&docId=1" class="btn btn-success btn-sm">
                            &DownArrowBar; Stream Content (REST API)
                        </a>
                    </div>
                </div>
            </div>
        </div>

    </div>

    <!-- Row 2: Multipart Upload & Discovery Analysis -->
    <div class="row g-4 mb-4">

        <!-- Card 3: Multipart Ingestion -->
        <div class="col-md-6">
            <div class="card h-100 border-top border-4 border-primary">
                <div class="card-body">
                    <h5 class="card-title text-primary fw-bold mb-3">3. Multipart Document Upload</h5>
                    <p class="text-muted small">
                        Uploads binary document from Servlet via <code>@MultipartConfig</code> and forwards it as a multipart stream to the Spring Boot REST API with Bearer token authentication.
                    </p>

                    <form action="<%= request.getContextPath() %>/legacy-upload" method="post" enctype="multipart/form-data">
                        <div class="mb-3">
                            <label class="form-label small fw-semibold">Choose File to Upload</label>
                            <input type="file" name="file" class="form-control form-control-sm" required>
                        </div>
                        <div class="mb-3">
                            <label class="form-label small fw-semibold">Document Item Type</label>
                            <select name="itemType" class="form-select form-select-sm">
                                <option value="INSURANCE_POLICY">INSURANCE_POLICY (IBM CM Type)</option>
                                <option value="CUSTOMER_CLAIM">CUSTOMER_CLAIM</option>
                                <option value="ID_PROOF">ID_PROOF</option>
                                <option value="GENERAL">GENERAL</option>
                            </select>
                        </div>
                        <button type="submit" class="btn btn-primary btn-sm">
                            &UparrowBar; Upload Document to Target REST API
                        </button>
                    </form>
                </div>
            </div>
        </div>

        <!-- Card 4: Discovery & Gap Analysis -->
        <div class="col-md-6">
            <div class="card h-100 border-top border-4 border-info">
                <div class="card-body">
                    <h5 class="card-title text-info fw-bold mb-3">4. Structured Gap-Analysis Summary</h5>
                    <p class="text-muted small">
                        Reconstructing legacy behavior before migration (Discovery &rarr; Gap-Analysis &rarr; Implementation):
                    </p>

                    <div class="table-responsive">
                        <table class="table table-sm table-bordered small">
                            <thead class="table-light">
                                <tr>
                                    <th>Legacy (IBM CM / Db2)</th>
                                    <th>Target (Spring Boot REST)</th>
                                </tr>
                            </thead>
                            <tbody>
                                <tr>
                                    <td><code>DKDatastoreICM</code> / JDBC</td>
                                    <td><code>HttpClient</code> / <code>RestTemplate</code></td>
                                </tr>
                                <tr>
                                    <td>Direct Db2 Credentials</td>
                                    <td>OAuth2 Client-Credentials (Bearer)</td>
                                </tr>
                                <tr>
                                    <td><code>DKLobICM.getContent()</code></td>
                                    <td><code>StreamingResponseBody</code> (Chunked)</td>
                                </tr>
                                <tr>
                                    <td><code>ICMSTITEMS001001</code></td>
                                    <td><code>GET /api/v1/documents/{id}</code></td>
                                </tr>
                            </tbody>
                        </table>
                    </div>

                    <a href="<%= request.getContextPath() %>/legacy-cm?mode=gap" target="_blank" class="btn btn-outline-info btn-sm">
                        &OpenCurlyDoubleQuote; View Complete Gap-Analysis JSON
                    </a>
                </div>
            </div>
        </div>

    </div>

    <!-- Microservices Status Footer -->
    <div class="card bg-white p-3 text-center">
        <h6 class="fw-bold mb-2">Active Architecture Ports</h6>
        <div class="d-flex justify-content-center gap-4 text-muted small">
            <div><strong>Port 8080:</strong> IBM WebSphere Liberty J2EE EAR Application (<code>legacy-cm-ear.ear</code>)</div>
            <div><strong>Port 8081:</strong> Spring Boot Microservice 1 (Client with RestTemplate)</div>
            <div><strong>Port 8082:</strong> Spring Boot Microservice 2 (Target REST API &amp; OAuth2)</div>
            <div><strong>Port 5432:</strong> PostgreSQL (Simulating Db2 ICM Schema &amp; Data)</div>
        </div>
    </div>

</div>

</body>
</html>
