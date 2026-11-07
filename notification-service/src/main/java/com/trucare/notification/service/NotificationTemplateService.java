package com.trucare.notification.service;

import com.trucare.notification.dto.*;
import com.trucare.notification.exception.NotificationException;
import com.trucare.notification.model.NotificationTemplate;
import com.trucare.notification.repository.NotificationTemplateRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationTemplateService {
    private final NotificationTemplateRepository templates;

    public NotificationTemplateService(NotificationTemplateRepository templates) {
        this.templates = templates;
    }

    @Transactional
    public NotificationTemplateResponse create(NotificationTemplateRequest request) {
        if (templates.findByTemplateCode(request.templateCode()).isPresent()) {
            throw new NotificationException(HttpStatus.CONFLICT, "Template code already exists");
        }
        if (templates.findByEventTypeAndChannelAndActiveTrue(request.eventType(), request.channel()).isPresent()
                && request.active()) {
            throw new NotificationException(HttpStatus.CONFLICT,
                    "An active template already exists for this event and channel");
        }
        NotificationTemplate template = new NotificationTemplate();
        apply(template, request);
        return response(templates.saveAndFlush(template));
    }

    @Transactional
    public NotificationTemplateResponse update(String code, NotificationTemplateRequest request) {
        if (!code.equals(request.templateCode())) {
            throw new NotificationException(HttpStatus.BAD_REQUEST, "Template code cannot be changed");
        }
        NotificationTemplate template = templates.findByTemplateCode(code).orElseThrow(
                () -> new NotificationException(HttpStatus.NOT_FOUND, "Notification template was not found"));
        if (request.active()) {
            templates.findByEventTypeAndChannelAndActiveTrue(request.eventType(), request.channel())
                    .filter(existing -> !existing.getId().equals(template.getId()))
                    .ifPresent(existing -> {
                        throw new NotificationException(HttpStatus.CONFLICT,
                                "An active template already exists for this event and channel");
                    });
        }
        apply(template, request);
        return response(templates.saveAndFlush(template));
    }

    @Transactional(readOnly = true)
    public NotificationTemplateResponse get(String code) {
        return response(templates.findByTemplateCode(code).orElseThrow(
                () -> new NotificationException(HttpStatus.NOT_FOUND, "Notification template was not found")));
    }

    @Transactional(readOnly = true)
    public Page<NotificationTemplateResponse> list(Pageable pageable) {
        return templates.findAllByOrderByUpdatedAtDesc(pageable).map(this::response);
    }

    @Transactional(readOnly = true)
    public NotificationTemplate resolve(String code, com.trucare.notification.model.NotificationEventType event,
                                        com.trucare.notification.model.NotificationChannel channel) {
        NotificationTemplate template = (code == null || code.isBlank()
                ? templates.findByEventTypeAndChannelAndActiveTrue(event, channel)
                : templates.findByTemplateCode(code))
                .orElseThrow(() -> new NotificationException(HttpStatus.NOT_FOUND,
                        "No active notification template exists for this event and channel"));
        if (!template.isActive() || template.getEventType() != event || template.getChannel() != channel) {
            throw new NotificationException(HttpStatus.CONFLICT,
                    "Template is inactive or does not match the requested event and channel");
        }
        return template;
    }

    private void apply(NotificationTemplate template, NotificationTemplateRequest request) {
        template.setTemplateCode(request.templateCode());
        template.setEventType(request.eventType());
        template.setChannel(request.channel());
        template.setSubjectTemplate(request.subjectTemplate());
        template.setMessageTemplate(request.messageTemplate());
        template.setActive(request.active());
        template.setActiveTemplateKey(request.active()
                ? request.eventType().name() + ":" + request.channel().name() : null);
    }

    private NotificationTemplateResponse response(NotificationTemplate template) {
        return new NotificationTemplateResponse(template.getTemplateCode(), template.getEventType(),
                template.getChannel(), template.getSubjectTemplate(), template.getMessageTemplate(),
                template.isActive(), template.getVersion(), template.getCreatedAt(), template.getUpdatedAt());
    }
}
