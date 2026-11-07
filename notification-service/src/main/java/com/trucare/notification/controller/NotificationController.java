package com.trucare.notification.controller;

import com.trucare.notification.dto.*;
import com.trucare.notification.exception.NotificationException;
import com.trucare.notification.model.*;
import com.trucare.notification.service.NotificationEventHandler;
import com.trucare.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService service;
    private final NotificationEventHandler eventHandler;

    public NotificationController(NotificationService service, NotificationEventHandler eventHandler) {
        this.service = service;
        this.eventHandler = eventHandler;
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateNotificationRequest request) {
        NotificationService.CreationResult result = eventHandler.handle(request, idempotencyKey);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(result.notification());
    }

    @GetMapping("/{notificationId}")
    public NotificationResponse get(@PathVariable Long notificationId, Authentication authentication) {
        NotificationResponse response = service.get(notificationId);
        authorizeRecipient(authentication, response.recipientId());
        return response;
    }

    @GetMapping("/reference/{notificationReference}")
    public NotificationResponse getByReference(@PathVariable String notificationReference,
                                               Authentication authentication) {
        if (!isPrivileged(authentication)) {
            throw new NotificationException(HttpStatus.FORBIDDEN,
                    "Notification reference lookup is restricted to internal services");
        }
        return service.getByReference(notificationReference);
    }

    @GetMapping("/recipient/{recipientId}")
    public Page<NotificationResponse> getForRecipient(@PathVariable String recipientId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        authorizeRecipient(authentication, recipientId);
        return service.getForRecipient(recipientId, capped(pageable));
    }

    @GetMapping("/in-app/{recipientId}")
    public Page<NotificationResponse> getInApp(@PathVariable String recipientId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        authorizeRecipient(authentication, recipientId);
        return service.getInApp(recipientId, false, capped(pageable));
    }

    @GetMapping("/in-app/{recipientId}/unread")
    public Page<NotificationResponse> getUnreadInApp(@PathVariable String recipientId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication authentication) {
        authorizeRecipient(authentication, recipientId);
        return service.getInApp(recipientId, true, capped(pageable));
    }

    @RequestMapping(path = "/{notificationId}/read", method = {RequestMethod.PATCH, RequestMethod.POST})
    public NotificationResponse markRead(@PathVariable Long notificationId, Authentication authentication) {
        return service.markRead(notificationId, authentication.getName(), isPrivileged(authentication));
    }

    @PostMapping("/{notificationId}/retry")
    public NotificationResponse retry(@PathVariable Long notificationId) {
        return service.retry(notificationId);
    }

    @GetMapping
    public Page<NotificationResponse> search(
            @RequestParam(required = false) NotificationEventType eventType,
            @RequestParam(required = false) NotificationChannel channel,
            @RequestParam(required = false) NotificationStatus status,
            @RequestParam(required = false) String recipientId,
            @RequestParam(required = false) RelatedEntityType relatedEntityType,
            @RequestParam(required = false) String relatedEntityId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(eventType, channel, status, recipientId, relatedEntityType, relatedEntityId,
                from, to, capped(pageable));
    }

    private void authorizeRecipient(Authentication authentication, String recipientId) {
        if (!isPrivileged(authentication) && !authentication.getName().equals(recipientId)) {
            throw new NotificationException(HttpStatus.FORBIDDEN,
                    "Recipient resources can only be accessed by their owner");
        }
    }

    private boolean isPrivileged(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority ->
                authority.getAuthority().equals("ROLE_NOTIFICATION_INTERNAL")
                        || authority.getAuthority().equals("ROLE_NOTIFICATION_ADMIN"));
    }

    private Pageable capped(Pageable pageable) {
        int size = Math.min(pageable.getPageSize(), 100);
        return PageRequest.of(pageable.getPageNumber(), size, pageable.getSort());
    }
}
