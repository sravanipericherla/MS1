package com.legacy.cm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Modern REST Client demonstrating how legacy J2EE code is migrated to consume
 * Spring Boot REST APIs using OAuth2 client-credentials authentication and streaming.
 */
public class ModernContentRestClient {

    private static final Logger LOGGER = Logger.getLogger(ModernContentRestClient.class.getName());

    private final String baseUrl;
    private final String clientId;
    private final String clientSecret;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    // In-memory OAuth2 token cache
    private String cachedAccessToken = null;
    private long tokenExpiryEpochMs = 0;

    public ModernContentRestClient(String baseUrl, String clientId, String clientSecret) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Obtains an OAuth2 Bearer token using the Client-Credentials flow.
     * Caches the token in-memory until it expires.
     */
    public synchronized String getAccessToken() throws IOException, InterruptedException {
        long now = System.currentTimeMillis();
        if (cachedAccessToken != null && now < (tokenExpiryEpochMs - 60000)) {
            LOGGER.fine("[OAuth2 Client] Reusing active cached access token.");
            return cachedAccessToken;
        }

        String tokenEndpoint = baseUrl + "/oauth/token";
        LOGGER.info("[OAuth2 Client] Requesting new access token from: " + tokenEndpoint);

        String formBody = "grant_type=client_credentials" +
                "&client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8) +
                "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8) +
                "&scope=" + URLEncoder.encode("documents:read documents:write", StandardCharsets.UTF_8);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenEndpoint))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            this.cachedAccessToken = root.path("access_token").asText();
            long expiresInSeconds = root.path("expires_in").asLong(3600);
            this.tokenExpiryEpochMs = now + (expiresInSeconds * 1000);

            LOGGER.info("[OAuth2 Client] Successfully obtained access token (expires in " + expiresInSeconds + "s)");
            return cachedAccessToken;
        } else {
            LOGGER.severe("[OAuth2 Client] Failed to obtain token: HTTP " + response.statusCode() + " -> " + response.body());
            throw new IOException("OAuth2 token request failed: HTTP " + response.statusCode());
        }
    }

    /**
     * Streams document content chunk-by-chunk via HTTP GET from the modern Spring Boot REST API
     * directly into the provided destination OutputStream.
     */
    public boolean streamDocument(long docId, OutputStream targetStream) throws IOException, InterruptedException {
        String token = getAccessToken();
        String targetUrl = baseUrl + "/api/v1/documents/" + docId + "/content";

        LOGGER.info("[Modern REST Stream] Streaming document ID " + docId + " from: " + targetUrl);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl))
                .header("Authorization", "Bearer " + token)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() == 200) {
            try (InputStream inputStream = response.body()) {
                byte[] buffer = new byte[8192]; // 8KB buffer for streaming
                int bytesRead;
                long totalBytes = 0;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    targetStream.write(buffer, 0, bytesRead);
                    totalBytes += bytesRead;
                }
                targetStream.flush();
                LOGGER.info("[Modern REST Stream] Successfully streamed " + totalBytes + " bytes for document ID " + docId);
                return true;
            }
        } else {
            LOGGER.warning("[Modern REST Stream] Server returned status: " + response.statusCode());
            return false;
        }
    }

    /**
     * Uploads a document via multipart/form-data to the Spring Boot REST API.
     */
    public String uploadDocument(String filename, String mimeType, byte[] fileBytes, String itemType) throws IOException, InterruptedException {
        String token = getAccessToken();
        String uploadUrl = baseUrl + "/api/v1/documents";
        String boundary = "----WebKitFormBoundary" + UUID.randomUUID().toString().replace("-", "");

        LOGGER.info("[Modern REST Upload] Uploading '" + filename + "' (" + fileBytes.length + " bytes) to " + uploadUrl);

        ByteArrayOutputStream bodyStream = new ByteArrayOutputStream();
        // File part
        bodyStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        bodyStream.write(("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        bodyStream.write(("Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        bodyStream.write(fileBytes);
        bodyStream.write("\r\n".getBytes(StandardCharsets.UTF_8));

        // itemType part
        bodyStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        bodyStream.write("Content-Disposition: form-data; name=\"itemType\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        bodyStream.write(itemType.getBytes(StandardCharsets.UTF_8));
        bodyStream.write("\r\n".getBytes(StandardCharsets.UTF_8));

        // author part
        bodyStream.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        bodyStream.write("Content-Disposition: form-data; name=\"author\"\r\n\r\n".getBytes(StandardCharsets.UTF_8));
        bodyStream.write("LEGACY_J2EE_EAR".getBytes(StandardCharsets.UTF_8));
        bodyStream.write("\r\n".getBytes(StandardCharsets.UTF_8));

        // Closing boundary
        bodyStream.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(bodyStream.toByteArray()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    /**
     * Retrieves document metadata as JSON from the modern REST API.
     */
    public String getDocumentMetadata(long docId) throws IOException, InterruptedException {
        String token = getAccessToken();
        String url = baseUrl + "/api/v1/documents/" + docId;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }
}
