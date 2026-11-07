package com.trucare.settlement.service;

import com.trucare.settlement.mapper.SettlementMapperImpl;
import com.trucare.settlement.model.RecipientType;
import com.trucare.settlement.model.Settlement;
import com.trucare.settlement.model.SettlementAudit;
import com.trucare.settlement.model.SettlementPaymentTransaction;
import com.trucare.settlement.model.SettlementStatus;
import com.trucare.settlement.model.SettlementType;
import com.trucare.settlement.model.PaymentTransactionStatus;
import com.trucare.settlement.repository.SettlementAuditRepository;
import com.trucare.settlement.repository.SettlementPaymentTransactionRepository;
import com.trucare.settlement.repository.SettlementRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import({SettlementCreationTransaction.class, SettlementMapperImpl.class})
class SettlementPersistenceTest {

    @Autowired
    private SettlementRepository settlementRepository;

    @Autowired
    private SettlementAuditRepository auditRepository;

    @Autowired
    private SettlementPaymentTransactionRepository paymentRepository;

    @Autowired
    private SettlementCreationTransaction creationTransaction;

    @Test
    void persistsInitialAuditWithSettlement() {
        creationTransaction.persist(settlement("key-1", "CLM1001"), "operator");

        assertEquals(1, settlementRepository.count());
        assertEquals(1, auditRepository.count());
        SettlementAudit audit = auditRepository.findAll().get(0);
        assertEquals(SettlementStatus.PENDING, audit.getNewStatus());
        assertEquals("operator", audit.getPerformedBy());
    }

    @Test
    void databasePreventsDuplicateIdempotencyKeys() {
        settlementRepository.saveAndFlush(settlement("key-1", "CLM1001"));

        assertThrows(DataIntegrityViolationException.class,
                () -> settlementRepository.saveAndFlush(settlement("key-1", "CLM1002")));
    }

    @Test
    void databasePreventsDuplicateSuccessfulPaymentReferences() {
        Settlement first = settlementRepository.saveAndFlush(settlement("key-1", "CLM1001"));
        Settlement second = settlementRepository.saveAndFlush(settlement("key-2", "CLM1002"));
        paymentRepository.saveAndFlush(payment(first, "PAY-1"));

        assertThrows(DataIntegrityViolationException.class,
                () -> paymentRepository.saveAndFlush(payment(second, "PAY-1")));
    }

    private Settlement settlement(String idempotencyKey, String claimId) {
        Settlement settlement = new Settlement();
        settlement.setSettlementNumber("STL-" + claimId);
        settlement.setClaimId(claimId);
        settlement.setIdempotencyKey(idempotencyKey);
        settlement.setRequestHash("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        settlement.setMemberId("M501");
        settlement.setProviderId("P201");
        settlement.setPolicyId(1001L);
        settlement.setSettlementType(SettlementType.REIMBURSEMENT);
        settlement.setApprovedClaimAmount(new BigDecimal("120000.00"));
        settlement.setDeductibleAmount(new BigDecimal("5000.00"));
        settlement.setCopayAmount(new BigDecimal("10000.00"));
        settlement.setNonPayableAmount(BigDecimal.ZERO.setScale(2));
        settlement.setPayableAmount(BigDecimal.ZERO.setScale(2));
        settlement.setCurrency("INR");
        settlement.setSettlementStatus(SettlementStatus.PENDING);
        settlement.setRecipientType(RecipientType.MEMBER);
        settlement.setRecipientId("M501");
        return settlement;
    }

    private SettlementPaymentTransaction payment(Settlement settlement, String reference) {
        SettlementPaymentTransaction payment = new SettlementPaymentTransaction();
        payment.setSettlement(settlement);
        payment.setPaymentReference(reference);
        payment.setTransactionStatus(PaymentTransactionStatus.SUCCEEDED);
        payment.setAmount(new BigDecimal("105000.00"));
        payment.setCurrency("INR");
        payment.setDetails("Payment completed");
        return payment;
    }
}
