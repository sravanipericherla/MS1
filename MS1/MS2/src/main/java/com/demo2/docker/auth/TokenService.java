package com.demo2.docker.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates an enterprise OAuth2 Authorization Server (Client-Credentials flow).
 * Issues and validates Bearer tokens for legacy applications and microservices.
 */
@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    // In-memory store of active tokens and their expiration timestamp in milliseconds
    private final Map<String, Long> activeTokens = new ConcurrentHashMap<>();

    // Master demo token for quick manual testing
    public static final String DEMO_MASTER_TOKEN = "demo-cm-token-12345";

    // Allowed clients for the client_credentials flow
    private static final String VALID_CLIENT_ID = "legacy-app";
    private static final String VALID_CLIENT_SECRET = "cm-secret-123";
    private static final String DEFAULT_SCOPE = "documents:read documents:write";
    private static final long TOKEN_VALIDITY_SECONDS = 3600; // 1 hour

    public TokenService() {
        // Pre-register demo master token with practically infinite expiry for developer convenience
        activeTokens.put(DEMO_MASTER_TOKEN, System.currentTimeMillis() + (1000L * 60 * 60 * 24 * 365));
    }

    public boolean validateClientCredentials(String clientId, String clientSecret, String grantType) {
        if (!"client_credentials".equalsIgnoreCase(grantType)) {
            log.warn("Invalid grant_type requested: {}. Expected 'client_credentials'.", grantType);
            return false;
        }

        // Accept configured legacy app credentials or any test client prefixed with 'demo'
        if ((VALID_CLIENT_ID.equals(clientId) && VALID_CLIENT_SECRET.equals(clientSecret))
                || ("demo-client".equals(clientId) && "demo-secret".equals(clientSecret))) {
            return true;
        }

        log.warn("Authentication failed for clientId='{}'.", clientId);
        return false;
    }

    public OAuthTokenResponse generateToken(String scope) {
        String token = "cm_token_" + UUID.randomUUID().toString().replace("-", "");
        long expiresAt = System.currentTimeMillis() + (TOKEN_VALIDITY_SECONDS * 1000);
        activeTokens.put(token, expiresAt);

        log.info("Issued new OAuth2 Bearer token: {} (valid for {}s)", token, TOKEN_VALIDITY_SECONDS);

        return OAuthTokenResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(TOKEN_VALIDITY_SECONDS)
                .scope(scope != null && !scope.isBlank() ? scope : DEFAULT_SCOPE)
                .build();
    }

    public boolean isTokenValid(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        // Strip "Bearer " prefix if present
        String cleanToken = token.startsWith("Bearer ") ? token.substring(7).trim() : token.trim();

        Long expiry = activeTokens.get(cleanToken);
        if (expiry == null) {
            log.warn("Token not recognized: {}", cleanToken);
            return false;
        }

        if (System.currentTimeMillis() > expiry) {
            log.warn("Token expired: {}", cleanToken);
            activeTokens.remove(cleanToken);
            return false;
        }

        return true;
    }
}
