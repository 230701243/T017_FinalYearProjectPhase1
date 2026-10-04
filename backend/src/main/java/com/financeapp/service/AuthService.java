package com.financeapp.service;

import com.financeapp.dto.AuthResponse;
import com.financeapp.dto.LoginRequest;
import com.financeapp.dto.RegisterRequest;
import com.financeapp.dto.UpdateProfileRequest;
import com.financeapp.dto.UserProfileDto;
import com.financeapp.model.User;
import com.financeapp.repository.UserRepository;
import com.financeapp.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new RuntimeException("Username '" + request.username() + "' is already taken.");
        }
        if (userRepository.findByEmail(request.email()).isPresent()) {
            throw new RuntimeException("Email '" + request.email() + "' is already registered.");
        }

        String country = (request.country() != null && !request.country().isBlank()) ? request.country().trim().toUpperCase() : "US";
        String preferredCurrency = (request.preferredCurrency() != null && !request.preferredCurrency().isBlank()) ? request.preferredCurrency().trim().toUpperCase() : "USD";

        User user = new User(
                request.username(),
                request.email(),
                passwordEncoder.encode(request.password()),
                country,
                preferredCurrency
        );
        if (request.preferredLanguage() != null && !request.preferredLanguage().isBlank()) {
            user.setPreferredLanguage(request.preferredLanguage().trim());
        }
        userRepository.save(user);

        var userDetails = new org.springframework.security.core.userdetails.User(
                user.getUsername(), user.getPassword(), Collections.emptyList());

        String token = jwtService.generateToken(userDetails);
        return new AuthResponse(token, user.getUsername(), user.getEmail(), user.getCountry(), user.getPreferredCurrency(), user.getPreferredLanguage(), "Registration successful");
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new RuntimeException("User not found"));

        var userDetails = new org.springframework.security.core.userdetails.User(
                user.getUsername(), user.getPassword(), Collections.emptyList());

        String token = jwtService.generateToken(userDetails);
        return new AuthResponse(token, user.getUsername(), user.getEmail(), user.getCountry(), user.getPreferredCurrency(), user.getPreferredLanguage(), "Login successful");
    }

    public UserProfileDto getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        return new UserProfileDto(user);
    }

    public UserProfileDto updateProfile(String username, UpdateProfileRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        if (request.getCountry() != null && !request.getCountry().isBlank()) {
            user.setCountry(request.getCountry().trim().toUpperCase());
        }
        if (request.getPreferredCurrency() != null && !request.getPreferredCurrency().isBlank()) {
            user.setPreferredCurrency(request.getPreferredCurrency().trim().toUpperCase());
        }
        if (request.getHasHealthInsurance() != null) {
            user.setHasHealthInsurance(request.getHasHealthInsurance());
        }
        if (request.getHasLifeInsurance() != null) {
            user.setHasLifeInsurance(request.getHasLifeInsurance());
        }

        User saved = userRepository.save(user);
        return new UserProfileDto(saved);
    }

    public UserProfileDto updateLanguage(String username, String preferredLanguage) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));

        if (preferredLanguage != null && !preferredLanguage.isBlank()) {
            user.setPreferredLanguage(preferredLanguage.trim());
        }

        User saved = userRepository.save(user);
        return new UserProfileDto(saved);
    }
}
