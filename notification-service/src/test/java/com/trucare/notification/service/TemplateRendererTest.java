package com.trucare.notification.service;

import com.trucare.notification.exception.NotificationException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TemplateRendererTest {
    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void rendersLiteralPlaceholdersWithoutEvaluatingExpressions() {
        assertEquals("Hello Ada; {{7*7}}",
                renderer.render("Hello {{ name }}; {{7*7}}", Map.of("name", "Ada")));
    }

    @Test
    void rejectsMissingVariables() {
        assertThrows(NotificationException.class,
                () -> renderer.render("Claim {{claimId}}", Map.of()));
    }
}
