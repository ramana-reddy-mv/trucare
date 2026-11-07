package com.trucare.payment.service;

import com.trucare.payment.client.SettlementClient;
import com.trucare.payment.client.SettlementResponse;
import com.trucare.payment.dto.InitiatePaymentRequest;
import com.trucare.payment.dto.PaymentInitiationResult;
import com.trucare.payment.dto.PaymentResponse;
import com.trucare.payment.exception.PaymentException;
import com.trucare.payment.model.*;
import com.trucare.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock private SettlementClient settlementClient;
    @Mock private PaymentStore store;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentFingerprint fingerprint;
    @Mock private PaymentGatewayProcessor gatewayProcessor;
    @Mock private SettlementCallbackService callbackService;

    private PaymentService service;
    private InitiatePaymentRequest request;
    private final PaymentResponse response = new PaymentResponse();

    @BeforeEach
    void setUp() {
        service = new PaymentService(settlementClient, store, paymentRepository, fingerprint,
                gatewayProcessor, callbackService);
        request = new InitiatePaymentRequest();
        request.setSettlementId(1001L);
        request.setPaymentMethod("BANK_TRANSFER");
    }

    @Test
    void createsPaymentFromSettlementAuthoritativeValues() {
        when(fingerprint.calculate(request)).thenReturn("hash");
        when(store.getExistingByKey("key")).thenReturn(null);
        when(settlementClient.getSettlement(1001L)).thenReturn(readySettlement());
        response.setId(7L);
        when(store.create(any(Payment.class)))
                .thenReturn(new PaymentStore.CreatedPayment(response, 99L));
        when(store.get(7L)).thenReturn(response);

        assertSame(response, service.initiate(request, "key").payment());

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(store).create(captor.capture());
        Payment payment = captor.getValue();
        assertEquals(new BigDecimal("105000.00"), payment.getAmount());
        assertEquals("M501", payment.getMemberId());
        assertEquals("P201", payment.getRecipientId());
        assertEquals(RecipientType.PROVIDER, payment.getRecipientType());
        assertEquals(SettlementType.CASHLESS, payment.getSettlementType());
        assertEquals("INR", payment.getCurrency());
        assertEquals(PaymentStatus.INITIATED, payment.getPaymentStatus());
        verify(callbackService).deliver(99L);
    }

    @Test
    void returnsSameResultForSameIdempotencyKeyAndPayload() {
        when(fingerprint.calculate(request)).thenReturn("hash");
        when(store.getExistingByKey("key")).thenReturn(new PaymentStore.ExistingPayment("hash", response));

        PaymentInitiationResult result = service.initiate(request, "key");

        assertTrue(result.replayed());
        assertSame(response, result.payment());
        verifyNoInteractions(settlementClient, callbackService);
    }

    @Test
    void rejectsDifferentPayloadForExistingKey() {
        when(fingerprint.calculate(request)).thenReturn("new-hash");
        when(store.getExistingByKey("key")).thenReturn(new PaymentStore.ExistingPayment("old-hash", response));

        assertThrows(PaymentException.class, () -> service.initiate(request, "key"));
        verifyNoInteractions(settlementClient);
    }

    @Test
    void rejectsSettlementNotReady() {
        when(fingerprint.calculate(request)).thenReturn("hash");
        when(store.getExistingByKey("key")).thenReturn(null);
        when(settlementClient.getSettlement(1001L)).thenReturn(new SettlementResponse(
                1001L, "STL-1", "CLM-1", "M501", 12L, "CASHLESS",
                new BigDecimal("50.00"), "INR", "CALCULATED", "PROVIDER", "P201"));

        assertThrows(PaymentException.class, () -> service.initiate(request, "key"));
        verify(store, never()).create(any());
    }

    @Test
    void rejectsZeroSettlementPayable() {
        when(fingerprint.calculate(request)).thenReturn("hash");
        when(store.getExistingByKey("key")).thenReturn(null);
        SettlementResponse zero = new SettlementResponse(1001L, "STL-1", "CLM-1", "M501", 12L,
                "CASHLESS", BigDecimal.ZERO, "INR", "READY_FOR_PAYMENT", "PROVIDER", "P201");
        when(settlementClient.getSettlement(1001L)).thenReturn(zero);

        assertThrows(PaymentException.class, () -> service.initiate(request, "key"));
    }

    private SettlementResponse readySettlement() {
        return new SettlementResponse(1001L, "STL-1", "CLM-1", "M501", 12L, "CASHLESS",
                new BigDecimal("105000.00"), "INR", "READY_FOR_PAYMENT", "PROVIDER", "P201");
    }
}
