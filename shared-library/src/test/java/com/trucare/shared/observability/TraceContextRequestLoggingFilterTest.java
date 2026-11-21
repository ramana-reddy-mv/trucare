package com.trucare.shared.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TraceContextRequestLoggingFilterTest {

    @Test
    void emitsTraceSpanAndCorrelationIdsAsStructuredLogValues() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(TraceContextRequestLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MDC.put(CorrelationId.MDC_KEY, "business-request-42");
            MDC.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
            MDC.put("spanId", "00f067aa0ba902b7");
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setMethod("GET");
            request.setRequestURI("/api/members/validate/sample");
            new TraceContextRequestLoggingFilter().doFilter(
                    request,
                    new MockHttpServletResponse(),
                    (incoming, outgoing) -> { });
        } finally {
            MDC.remove(CorrelationId.MDC_KEY);
            MDC.remove("traceId");
            MDC.remove("spanId");
            logger.detachAppender(appender);
        }

        ILoggingEvent event = appender.list.stream()
                .filter(item -> "HTTP request received".equals(item.getFormattedMessage()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Servlet request log event was not emitted"));
        assertTrue("4bf92f3577b34da6a3ce929d0e0e4736".equals(event.getMDCPropertyMap().get("traceId")));
        assertTrue("00f067aa0ba902b7".equals(event.getMDCPropertyMap().get("spanId")));
        assertTrue("business-request-42".equals(
                        event.getMDCPropertyMap().get(CorrelationId.MDC_KEY)),
                "Expected structured log MDC to contain the correlation ID");
    }
}
