package com.trucare.payment.service;

import com.trucare.payment.mapper.PaymentMapperImpl;
import com.trucare.payment.dto.PaymentResponse;
import com.trucare.payment.model.*;
import com.trucare.payment.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({PaymentStore.class, PaymentMapperImpl.class})
class PaymentPersistenceTest {
    @Autowired private PaymentStore store;
    @Autowired private PaymentRepository payments;
    @Autowired private PaymentAuditRepository audits;
    @Autowired private PaymentTransactionRepository transactions;
    @Autowired private CallbackDeliveryRepository callbacks;

    @Test
    void storesPaymentAndCreationAuditTogether() {
        PaymentResponse response = store.create(payment("key-1", 500L)).response();

        assertNotNull(response.getId());
        assertEquals(1, payments.count());
        assertEquals(1, audits.count());
        assertEquals(1, callbacks.count());
        assertEquals(PaymentStatus.INITIATED, audits.findAll().get(0).getNewStatus());
    }

    @Test
    void uniqueIdempotencyKeyIsEnforcedByDatabase() {
        payments.saveAndFlush(payment("key-1", 500L));

        assertThrows(DataIntegrityViolationException.class,
                () -> payments.saveAndFlush(payment("key-1", 501L)));
    }

    @Test
    void recoversStaleCallbackClaimForBoundedRetry() {
        store.create(payment("key-1", 500L));
        CallbackDelivery delivery = callbacks.findAll().get(0);
        Instant cutoff = Instant.now().minusSeconds(60);
        delivery.setDeliveryStatus(CallbackStatus.DELIVERING);
        delivery.setAttemptCount(1);
        delivery.setDeliveryStartedAt(cutoff.minusSeconds(1));
        callbacks.saveAndFlush(delivery);

        store.recoverStaleCallback(delivery.getId(), cutoff);

        CallbackDelivery recovered = callbacks.findById(delivery.getId()).orElseThrow();
        assertEquals(CallbackStatus.PENDING, recovered.getDeliveryStatus());
        assertNull(recovered.getDeliveryStartedAt());
        assertNotNull(recovered.getNextAttemptAt());
    }

    @Test
    void settlementCanHaveOnlyOnePaymentOperation() {
        payments.saveAndFlush(payment("key-1", 500L));

        assertThrows(DataIntegrityViolationException.class,
                () -> payments.saveAndFlush(payment("key-2", 500L)));
    }

    @Test
    void providerReferenceIsUniqueAcrossAttempts() {
        Payment first = payments.saveAndFlush(payment("key-1", 500L));
        Payment second = payments.saveAndFlush(payment("key-2", 501L));
        transactions.saveAndFlush(transaction(first, "PAY-A1", "PROVIDER-REF"));

        assertThrows(DataIntegrityViolationException.class,
                () -> transactions.saveAndFlush(transaction(second, "PAY-A1", "PROVIDER-REF")));
    }

    private Payment payment(String key, Long settlementId) {
        Payment payment = new Payment();
        payment.setPaymentReference("PAY-" + settlementId);
        payment.setSettlementId(settlementId);
        payment.setSettlementNumber("STL-" + settlementId);
        payment.setClaimId("CLM-" + settlementId);
        payment.setMemberId("M501");
        payment.setRecipientId("P201");
        payment.setRecipientType(RecipientType.PROVIDER);
        payment.setSettlementType(SettlementType.CASHLESS);
        payment.setAmount(new BigDecimal("105000.00"));
        payment.setCurrency("INR");
        payment.setPaymentStatus(PaymentStatus.INITIATED);
        payment.setPaymentMethod("BANK_TRANSFER");
        payment.setIdempotencyKey(key);
        payment.setRequestHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        payment.setInitiatedAt(Instant.now());
        return payment;
    }

    private PaymentTransaction transaction(Payment payment, String txRef, String providerRef) {
        PaymentTransaction transaction = new PaymentTransaction();
        transaction.setPayment(payment);
        transaction.setTransactionReference(txRef);
        transaction.setProviderReference(providerRef);
        transaction.setAmount(payment.getAmount());
        transaction.setTransactionStatus(PaymentTransactionStatus.SUCCESS);
        transaction.setAttemptNumber(1);
        transaction.setRequestTimestamp(Instant.now());
        return transaction;
    }
}
