package com.demo2.docker.auth;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@Tag(name = "OAuth2 Authentication", description = "Endpoints for OAuth2 Client-Credentials token retrieval")
@CrossOrigin(origins = "*")
public class OAuthController {

    private static final Logger log = LoggerFactory.getLogger(OAuthController.class);
    private final TokenService tokenService;

    public OAuthController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Operation(
            summary = "Request OAuth2 Bearer Access Token (JSON body)",
            description = "Alternative JSON body format for OAuth2 client credentials.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Access token issued successfully",
                            content = @Content(schema = @Schema(implementation = OAuthTokenResponse.class))),
                    @ApiResponse(responseCode = "401", description = "Invalid client credentials")
            }
    )
    @PostMapping(
            value = {"/oauth/token"},
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> obtainTokenJson(@RequestBody(required = false) OAuthTokenRequest jsonRequest) {
        String grantType = jsonRequest != null && jsonRequest.getGrant_type() != null ? jsonRequest.getGrant_type() : "client_credentials";
        String clientId = jsonRequest != null && jsonRequest.getClient_id() != null ? jsonRequest.getClient_id() : "legacy-app";
        String clientSecret = jsonRequest != null && jsonRequest.getClient_secret() != null ? jsonRequest.getClient_secret() : "cm-secret-123";
        String scope = jsonRequest != null && jsonRequest.getScope() != null ? jsonRequest.getScope() : "documents:read documents:write";
        return processToken(grantType, clientId, clientSecret, scope);
    }

    private ResponseEntity<?> processToken(String grantType, String clientId, String clientSecret, String scope) {
        log.info("OAuth token request received: grant_type='{}', client_id='{}', scope='{}'", grantType, clientId, scope);

        if (!tokenService.validateClientCredentials(clientId, clientSecret, grantType)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "invalid_client",
                            "error_description", "Invalid client credentials or unsupported grant_type. Expected client_id='legacy-app', client_secret='cm-secret-123', grant_type='client_credentials'"
                    ));
        }

        OAuthTokenResponse tokenResponse = tokenService.generateToken(scope);
        return ResponseEntity.ok(tokenResponse);
    }
}
