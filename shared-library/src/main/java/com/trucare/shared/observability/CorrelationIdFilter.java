package com.trucare.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        List<String> values = java.util.Collections.list(request.getHeaders(CorrelationId.HEADER));
        if (values.size() > 1 || (!values.isEmpty() && !CorrelationId.isValid(values.get(0)))) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setHeader(CorrelationId.HEADER, CorrelationId.generate());
            return;
        }

        String correlationId = values.isEmpty() ? CorrelationId.generate() : values.get(0);
        String previousValue = MDC.get(CorrelationId.MDC_KEY);
        MDC.put(CorrelationId.MDC_KEY, correlationId);
        response.setHeader(CorrelationId.HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (previousValue == null) {
                MDC.remove(CorrelationId.MDC_KEY);
            } else {
                MDC.put(CorrelationId.MDC_KEY, previousValue);
            }
        }
    }
}
