package com.trucare.shared.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @Test
    void acceptsAndLogsValidatedCorrelationIdForRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "business-request-42");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (incoming, outgoing) ->
                assertEquals("business-request-42", MDC.get(CorrelationId.MDC_KEY)));

        assertEquals("business-request-42", response.getHeader(CorrelationId.HEADER));
        assertEquals(null, MDC.get(CorrelationId.MDC_KEY));
    }

    @Test
    void generatesCorrelationIdForInternalRequestWithoutHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (incoming, outgoing) ->
                assertTrue(CorrelationId.isValid(MDC.get(CorrelationId.MDC_KEY))));

        assertTrue(CorrelationId.isValid(response.getHeader(CorrelationId.HEADER)));
        assertEquals(null, MDC.get(CorrelationId.MDC_KEY));
    }

    @Test
    void rejectsUnsafeCorrelationIdsWithoutCallingApplication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationId.HEADER, "unsafe value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertEquals(400, response.getStatus());
        assertTrue(CorrelationId.isValid(response.getHeader(CorrelationId.HEADER)));
        verify(chain, never()).doFilter(request, response);
        assertEquals(null, MDC.get(CorrelationId.MDC_KEY));
    }
}
