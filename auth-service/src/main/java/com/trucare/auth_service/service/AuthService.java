package com.trucare.auth_service.service;

import com.trucare.auth_service.entity.UserCredential;
import com.trucare.auth_service.dto.AuthenticationRequest;
import com.trucare.auth_service.dto.RegistrationRequest;
import com.trucare.auth_service.dto.TokenResponse;
import com.trucare.auth_service.dto.UserResponse;
import com.trucare.auth_service.exception.RegistrationConflictException;
import com.trucare.auth_service.respository.UserCredentialRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {

    private final UserCredentialRepository userCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final long tokenExpirationSeconds;

    public AuthService(UserCredentialRepository userCredentialRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthenticationManager authenticationManager,
                       @org.springframework.beans.factory.annotation.Value(
                               "${security.jwt.expiration-seconds:900}") long tokenExpirationSeconds) {
        this.userCredentialRepository = userCredentialRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
        if (tokenExpirationSeconds < 1) {
            throw new IllegalStateException("security.jwt.expiration-seconds must be positive");
        }
        this.tokenExpirationSeconds = tokenExpirationSeconds;
    }

    public UserResponse register(RegistrationRequest request) {
        String username = request.name().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userCredentialRepository.existsByNameIgnoreCase(username)) {
            throw new RegistrationConflictException("Username is already registered");
        }
        if (userCredentialRepository.existsByEmailIgnoreCase(email)) {
            throw new RegistrationConflictException("Email is already registered");
        }
        UserCredential user = new UserCredential();
        user.setName(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRoles("PENDING_MEMBER");
        try {
            UserCredential saved = userCredentialRepository.saveAndFlush(user);
            return response(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new RegistrationConflictException("Username or email is already registered");
        }
    }

    public TokenResponse authenticate(AuthenticationRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        request.usernameOrEmail().trim(), request.password()));
        List<String> roles = authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority().startsWith("ROLE_")
                        ? authority.getAuthority().substring(5) : authority.getAuthority())
                .toList();
        String token = jwtService.createToken(authentication.getName(), roles, tokenExpirationSeconds);
        return new TokenResponse(token, "Bearer", tokenExpirationSeconds);
    }

    public void validateToken(String token) {
        jwtService.validateToken(token);
    }

    private UserResponse response(UserCredential user) {
        List<String> roles = Arrays.stream(user.getRoles().split(","))
                .map(String::trim).filter(role -> !role.isBlank()).toList();
        return new UserResponse(user.getName(), user.getEmail(), roles);
    }
}
