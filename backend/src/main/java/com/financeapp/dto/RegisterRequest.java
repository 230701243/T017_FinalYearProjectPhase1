package com.financeapp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank(message = "Username is required") String username,
    @Email(message = "Invalid email format") @NotBlank(message = "Email is required") String email,
    @NotBlank(message = "Password is required") @Size(min = 6, message = "Password must be at least 6 characters") String password,
    String country,
    String preferredCurrency,
    String preferredLanguage
) {
    public RegisterRequest(String username, String email, String password, String country, String preferredCurrency) {
        this(username, email, password, country, preferredCurrency, "en");
    }
}
