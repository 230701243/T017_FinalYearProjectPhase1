package com.financeapp.controller;

import com.financeapp.dto.UpdateProfileRequest;
import com.financeapp.dto.UserProfileDto;
import com.financeapp.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "*")
public class UserController {

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping({"/profile", "/preferences"})
    public ResponseEntity<UserProfileDto> getProfile(Authentication authentication) {
        return ResponseEntity.ok(authService.getProfile(authentication.getName()));
    }

    @PutMapping({"/profile", "/preferences"})
    public ResponseEntity<UserProfileDto> updateProfile(
            Authentication authentication,
            @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(authService.updateProfile(authentication.getName(), request));
    }

    @PutMapping("/language")
    public ResponseEntity<UserProfileDto> updateLanguage(
            Authentication authentication,
            @RequestBody java.util.Map<String, String> payload) {
        String preferredLanguage = payload != null ? payload.get("preferredLanguage") : "en";
        return ResponseEntity.ok(authService.updateLanguage(authentication.getName(), preferredLanguage));
    }
}
