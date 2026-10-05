package com.demo.docker;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

@RestController
@RequestMapping("/ms1")
@Tag(name = "MS1 Client API", description = "Spring Boot microservice consuming MS2 target REST APIs with RestTemplate, OAuth2 tokens, and streaming")
@CrossOrigin(origins = "*")
public class Ms1Controller {

    private static final Logger log = LoggerFactory.getLogger(Ms1Controller.class);

    private final RestTemplate restTemplate;
    private final String ms2ApiBaseUrl;
    private final String clientId;
    private final String clientSecret;

    // Cached token in memory
    private String cachedToken = null;

    @Autowired
    public Ms1Controller(
            RestTemplate restTemplate,
            @Value("${ms2.service.base-url}") String ms2ApiBaseUrl,
            @Value("${oauth2.client-id:legacy-app}") String clientId,
            @Value("${oauth2.client-secret:cm-secret-123}") String clientSecret
    ) {
        this.restTemplate = restTemplate;
        this.ms2ApiBaseUrl = ms2ApiBaseUrl.endsWith("/") ? ms2ApiBaseUrl : ms2ApiBaseUrl + "/";
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Operation(summary = "Health check greeting")
    @GetMapping("/greet")
    public String greet() {
        return "Greetings from Microservice 1 (Spring Boot Client)!";
    }

    /**
     * Obtains OAuth2 token from MS2 Authorization Server via Client-Credentials flow.
     */
    private synchronized String getOrFetchOAuth2Token() {
        String tokenUrl = ms2ApiBaseUrl + "oauth/token";
        log.info("MS1 requesting OAuth2 token from {}", tokenUrl);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("grant_type", "client_credentials");
            body.add("client_id", clientId);
            body.add("client_secret", clientSecret);
            body.add("scope", "documents:read documents:write");

            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(tokenUrl, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                this.cachedToken = (String) response.getBody().get("access_token");
                log.info("MS1 successfully received OAuth2 token: {}...", cachedToken.substring(0, Math.min(15, cachedToken.length())));
                return this.cachedToken;
            }
        } catch (Exception e) {
            log.error("MS1 failed to acquire OAuth2 token from {}: {}", tokenUrl, e.getMessage());
        }

        return "demo-cm-token-12345"; // Fallback to demo master token
    }

    @Operation(summary = "Upload Document via MS1 to MS2 (Multipart Stream)",
            description = "MS1 receives file and forwards it as multipart/form-data to MS2 with OAuth2 Bearer token")
    @PostMapping(value = "/documents/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "itemType", defaultValue = "MS1_CLIENT_DOC") String itemType
    ) {
        String targetUrl = ms2ApiBaseUrl + "api/v1/documents";
        String token = getOrFetchOAuth2Token();

        log.info("MS1 forwarding multipart upload to MS2: URL={}, filename={}", targetUrl, file.getOriginalFilename());

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(token);

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            });
            body.add("itemType", itemType);
            body.add("author", "MS1_SPRING_BOOT_CLIENT");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(targetUrl, requestEntity, Map.class);

            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());

        } catch (Exception e) {
            log.error("MS1 failed to forward document to MS2: {}", e.getMessage());
            return ResponseEntity.internalServerError().body("Error uploading to MS2: " + e.getMessage());
        }
    }

    @Operation(summary = "Stream Document from MS2 via MS1 (Binary Streaming)",
            description = "Streams document from MS2 through MS1 directly to client using InputStream/OutputStream buffers without high memory overhead.")
    @GetMapping("/documents/{id}/stream")
    public void streamDocumentFromMs2(
            @PathVariable Long id,
            HttpServletResponse clientResponse
    ) throws IOException {
        String targetUrl = ms2ApiBaseUrl + "api/v1/documents/" + id + "/content";
        String token = getOrFetchOAuth2Token();

        log.info("MS1 streaming document ID={} from MS2 at {}", id, targetUrl);

        restTemplate.execute(
                targetUrl,
                HttpMethod.GET,
                clientHttpRequest -> {
                    clientHttpRequest.getHeaders().setBearerAuth(token);
                },
                clientHttpResponse -> {
                    if (!clientHttpResponse.getStatusCode().is2xxSuccessful()) {
                        clientResponse.sendError(clientHttpResponse.getStatusCode().value(), "Error from MS2");
                        return null;
                    }

                    // Forward headers
                    HttpHeaders headers = clientHttpResponse.getHeaders();
                    if (headers.getContentType() != null) {
                        clientResponse.setContentType(headers.getContentType().toString());
                    }
                    if (headers.getContentDisposition().getFilename() != null) {
                        clientResponse.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + headers.getContentDisposition().getFilename() + "\"");
                    }
                    if (headers.getContentLength() > 0) {
                        clientResponse.setContentLengthLong(headers.getContentLength());
                    }

                    // Stream binary bytes from MS2 InputStream to client OutputStream
                    try (InputStream inputStream = clientHttpResponse.getBody();
                         OutputStream outputStream = clientResponse.getOutputStream()) {
                        StreamUtils.copy(inputStream, outputStream);
                        outputStream.flush();
                    }
                    return null;
                }
        );
    }
}