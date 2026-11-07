package com.trucare.auth_service.service;

import com.trucare.auth_service.entity.UserCredential;
import com.trucare.auth_service.respository.UserCredentialRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
public class AuthUserDetailsService implements UserDetailsService {
    private final UserCredentialRepository repository;

    public AuthUserDetailsService(UserCredentialRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        UserCredential credential = repository
                .findByNameIgnoreCaseOrEmailIgnoreCase(usernameOrEmail, usernameOrEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User was not found"));
        String roles = credential.getRoles() == null || credential.getRoles().isBlank()
                ? "MEMBER" : credential.getRoles();
        return User.withUsername(credential.getName())
                .password(credential.getPassword())
                .authorities(Arrays.stream(roles.split(","))
                        .map(String::trim)
                        .filter(role -> !role.isBlank())
                        .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                        .toArray(String[]::new))
                .build();
    }
}
