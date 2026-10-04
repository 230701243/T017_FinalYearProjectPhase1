package com.financeapp.dto;

public record AuthResponse(
    String token,
    String username,
    String email,
    String country,
    String preferredCurrency,
    String preferredLanguage,
    String message
) {
    public AuthResponse(String token, String username, String email, String country, String preferredCurrency, String message) {
        this(token, username, email, country, preferredCurrency, "en", message);
    }

    public AuthResponse(String token, String username, String message) {
        this(token, username, null, "US", "USD", "en", message);
    }
}
