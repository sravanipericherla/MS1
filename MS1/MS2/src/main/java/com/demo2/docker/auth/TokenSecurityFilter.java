package com.demo2.docker.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter to enforce OAuth2 Bearer token authentication on protected endpoints (/api/v1/documents/**).
 */
@Component
public class TokenSecurityFilter extends OncePerRequestFilter {

    private final TokenService tokenService;

    public TokenSecurityFilter(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Only enforce Bearer token verification on /api/v1/documents/**
        if (path.startsWith("/api/v1/documents")) {
            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

            if (authHeader == null || !authHeader.startsWith("Bearer ") || !tokenService.isTokenValid(authHeader)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write("""
                        {
                          "error": "unauthorized",
                          "error_description": "Protected endpoint: Missing or invalid Bearer token. Please call POST /oauth/token with client_id='legacy-app' and client_secret='cm-secret-123' to obtain a valid access token."
                        }
                        """);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
