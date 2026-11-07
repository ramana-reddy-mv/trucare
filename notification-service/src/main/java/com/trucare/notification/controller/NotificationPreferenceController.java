package com.trucare.notification.controller;

import com.trucare.notification.dto.NotificationPreferenceRequest;
import com.trucare.notification.dto.NotificationPreferenceResponse;
import com.trucare.notification.exception.NotificationException;
import com.trucare.notification.model.RecipientType;
import com.trucare.notification.service.NotificationStore;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications/recipient/{recipientId}/preferences")
public class NotificationPreferenceController {
    private final NotificationStore store;

    public NotificationPreferenceController(NotificationStore store) {
        this.store = store;
    }

    @GetMapping
    public List<NotificationPreferenceResponse> get(@PathVariable String recipientId,
                                                     @RequestParam RecipientType recipientType,
                                                     Authentication authentication) {
        authorize(authentication, recipientId);
        return store.getPreferences(recipientId, recipientType);
    }

    @PutMapping
    public NotificationPreferenceResponse update(@PathVariable String recipientId,
                                                  @RequestParam RecipientType recipientType,
                                                  @Valid @RequestBody NotificationPreferenceRequest request,
                                                  Authentication authentication) {
        authorize(authentication, recipientId);
        return store.savePreference(recipientId, recipientType, request.channel(), request.enabled());
    }

    private void authorize(Authentication authentication, String recipientId) {
        boolean privileged = authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_NOTIFICATION_INTERNAL")
                        || authority.getAuthority().equals("ROLE_NOTIFICATION_ADMIN"));
        if (!privileged && !authentication.getName().equals(recipientId)) {
            throw new NotificationException(HttpStatus.FORBIDDEN,
                    "Recipient preferences can only be managed by their owner");
        }
    }
}
