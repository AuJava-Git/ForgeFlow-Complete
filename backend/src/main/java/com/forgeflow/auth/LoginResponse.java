package com.forgeflow.auth;

public record LoginResponse(String tokenType, String accessToken, long expiresIn, String role, String displayName) {
}
