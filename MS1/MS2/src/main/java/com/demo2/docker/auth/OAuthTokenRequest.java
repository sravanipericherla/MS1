package com.demo2.docker.auth;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OAuthTokenRequest {
    private String grant_type;
    private String client_id;
    private String client_secret;
    private String scope;
}
