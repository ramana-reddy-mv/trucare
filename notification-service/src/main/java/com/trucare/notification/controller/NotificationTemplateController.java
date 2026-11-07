package com.trucare.notification.controller;

import com.trucare.notification.dto.NotificationTemplateRequest;
import com.trucare.notification.dto.NotificationTemplateResponse;
import com.trucare.notification.service.NotificationTemplateService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notification-templates")
public class NotificationTemplateController {
    private final NotificationTemplateService service;

    public NotificationTemplateController(NotificationTemplateService service) {
        this.service = service;
    }

    @PostMapping
    public NotificationTemplateResponse create(@Valid @RequestBody NotificationTemplateRequest request) {
        return service.create(request);
    }

    @GetMapping
    public Page<NotificationTemplateResponse> list(
            @PageableDefault(size = 20, sort = "updatedAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(capped(pageable));
    }

    @GetMapping("/{templateCode}")
    public NotificationTemplateResponse get(@PathVariable String templateCode) {
        return service.get(templateCode);
    }

    @PutMapping("/{templateCode}")
    public NotificationTemplateResponse update(@PathVariable String templateCode,
                                               @Valid @RequestBody NotificationTemplateRequest request) {
        return service.update(templateCode, request);
    }

    private Pageable capped(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
    }
}
