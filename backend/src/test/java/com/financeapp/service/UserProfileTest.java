package com.financeapp.service;

import com.financeapp.dto.AuthResponse;
import com.financeapp.dto.LoginRequest;
import com.financeapp.dto.RegisterRequest;
import com.financeapp.dto.UpdateProfileRequest;
import com.financeapp.dto.UserProfileDto;
import com.financeapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class UserProfileTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthService authService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void testRegistrationDefaultsToUsdAndUsWhenUnset() {
        RegisterRequest request = new RegisterRequest(
                "defaultUser",
                "default@example.com",
                "password123",
                null,
                null
        );

        AuthResponse response = authService.register(request);
        assertEquals("defaultUser", response.username());
        assertEquals("US", response.country());
        assertEquals("USD", response.preferredCurrency());

        UserProfileDto profile = authService.getProfile("defaultUser");
        assertEquals("US", profile.getCountry());
        assertEquals("USD", profile.getPreferredCurrency());
    }

    @Test
    void testRegistrationWithCustomCurrencyAndCountry() {
        RegisterRequest request = new RegisterRequest(
                "indiaUser",
                "india@example.com",
                "password123",
                "IN",
                "INR"
        );

        AuthResponse response = authService.register(request);
        assertEquals("indiaUser", response.username());
        assertEquals("IN", response.country());
        assertEquals("INR", response.preferredCurrency());

        UserProfileDto profile = authService.getProfile("indiaUser");
        assertEquals("IN", profile.getCountry());
        assertEquals("INR", profile.getPreferredCurrency());
    }

    @Test
    void testProfileUpdateFromAccountSettings() {
        RegisterRequest regReq = new RegisterRequest(
                "settingsUser",
                "settings@example.com",
                "password123",
                "US",
                "USD"
        );
        authService.register(regReq);

        UpdateProfileRequest updateReq = new UpdateProfileRequest("EU", "EUR");
        UserProfileDto updated = authService.updateProfile("settingsUser", updateReq);

        assertEquals("EU", updated.getCountry());
        assertEquals("EUR", updated.getPreferredCurrency());

        // Verify lookup
        UserProfileDto fetched = authService.getProfile("settingsUser");
        assertEquals("EU", fetched.getCountry());
        assertEquals("EUR", fetched.getPreferredCurrency());
    }

    @Test
    void testLoginReturnsCountryAndPreferredCurrency() {
        RegisterRequest regReq = new RegisterRequest(
                "loginUser",
                "login@example.com",
                "password123",
                "GB",
                "GBP"
        );
        authService.register(regReq);

        LoginRequest loginReq = new LoginRequest("loginUser", "password123");
        AuthResponse loginRes = authService.login(loginReq);

        assertEquals("loginUser", loginRes.username());
        assertEquals("GB", loginRes.country());
        assertEquals("GBP", loginRes.preferredCurrency());
    }
}
