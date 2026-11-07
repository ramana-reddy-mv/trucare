package com.trucare.auth_service.controller;


import com.trucare.auth_service.dto.AuthenticationRequest;
import com.trucare.auth_service.dto.RegistrationRequest;
import com.trucare.auth_service.dto.TokenResponse;
import com.trucare.auth_service.dto.UserResponse;
import com.trucare.auth_service.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping({"/addUser", "/register"})
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegistrationRequest request) {
        return authService.register(request);
    }

    @PostMapping({"/token", "/login"})
    public TokenResponse authenticate(@Valid @RequestBody AuthenticationRequest request) {
        return authService.authenticate(request);
    }

}
