package com.trucare.payment.service;

import com.trucare.payment.dto.PaymentResponse;
import com.trucare.payment.gateway.GatewayOutcome;
import com.trucare.payment.gateway.MockPaymentGateway;
import com.trucare.payment.model.*;
import com.trucare.payment.repository.CallbackDeliveryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentGatewayProcessorTest {

    @Test
    void mockGatewayReturnsConfiguredDeterministicFailure() {
        MockPaymentGateway gateway = new MockPaymentGateway(GatewayOutcome.FAILED);
        Payment payment = new Payment();
        payment.setPaymentReference("PAY-REF");

        var result = gateway.process(payment, "PAY-REF-A1", "PAY-REF");

        assertEquals(GatewayOutcome.FAILED, result.outcome());
        assertEquals("MOCK_DECLINED", result.failureCode());
        assertEquals("MOCK-PAY-REF-A1", result.providerReference());
    }
}
