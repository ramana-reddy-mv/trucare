package com.trucare.settlement.service;

import com.trucare.settlement.client.ClaimClient;
import com.trucare.settlement.dto.CreateSettlementRequest;
import com.trucare.settlement.dto.PaymentStatusRequest;
import com.trucare.settlement.dto.SettlementResponse;
import com.trucare.settlement.exception.ClaimServiceUnavailableException;
import com.trucare.settlement.exception.SettlementException;
import com.trucare.settlement.mapper.SettlementMapper;
import com.trucare.settlement.model.PaymentTransactionStatus;
import com.trucare.settlement.model.RecipientType;
import com.trucare.settlement.model.Settlement;
import com.trucare.settlement.model.SettlementAudit;
import com.trucare.settlement.model.SettlementPaymentTransaction;
import com.trucare.settlement.model.SettlementStatus;
import com.trucare.settlement.model.SettlementType;
import com.trucare.settlement.repository.SettlementAuditRepository;
import com.trucare.settlement.repository.SettlementPaymentTransactionRepository;
import com.trucare.settlement.repository.SettlementRepository;
import com.trucare.shared.claim.ClaimDTO;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private SettlementRepository settlementRepository;
    @Mock
    private SettlementAuditRepository auditRepository;
    @Mock
    private ClaimClient claimClient;
    @Mock
    private SettlementMapper mapper;
    @Mock
    private SettlementPaymentTransactionRepository paymentRepository;
    @Mock
    private SettlementRequestFingerprint fingerprint;
    @Mock
    private SettlementIdempotencyLookup idempotencyLookup;
    @Mock
    private SettlementCreationTransaction creationTransaction;

    private SettlementService service;
    private CreateSettlementRequest request;
    private ClaimDTO approvedClaim;
    private final SettlementResponse response = new SettlementResponse();

    @BeforeEach
    void setUp() {
        service = new SettlementService(settlementRepository, auditRepository, claimClient, mapper,
                new SettlementCalculationService(), paymentRepository, fingerprint,
                idempotencyLookup, creationTransaction);
        request = new CreateSettlementRequest();
        request.setClaimId("CLM1001");
        request.setPolicyId(1001L);
        request.setSettlementType(SettlementType.REIMBURSEMENT);
        request.setDeductibleAmount(new BigDecimal("5000"));
        request.setCopayAmount(new BigDecimal("10000"));
        request.setNonPayableAmount(BigDecimal.ZERO);
        request.setApprovedClaimAmount(BigDecimal.ONE);
        request.setMemberId("CALLER-MEMBER");
        request.setProviderId("CALLER-PROVIDER");

        approvedClaim = new ClaimDTO();
        approvedClaim.setClaimId("CLM1001");
        approvedClaim.setMemberId("M501");
        approvedClaim.setProviderId("P201");
        approvedClaim.setClaimAmount(new BigDecimal("120000"));
        approvedClaim.setClaimStatuses("APPROVED");
    }

    @Test
    void createsUsingAuthoritativeClaimAmountAndRecipientIdentifiers() {
        when(fingerprint.calculate(request)).thenReturn("hash-1");
        when(idempotencyLookup.find("key-1")).thenReturn(Optional.empty());
        when(claimClient.getClaimByBusinessId("CLM1001")).thenReturn(approvedClaim);
        when(creationTransaction.persist(any(Settlement.class), eq("operator"))).thenReturn(response);

        SettlementResponse result = service.create(request, "key-1", "operator");

        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(creationTransaction).persist(captor.capture(), eq("operator"));
        Settlement created = captor.getValue();
        assertSame(response, result);
        assertEquals(new BigDecimal("120000.00"), created.getApprovedClaimAmount());
        assertEquals("M501", created.getMemberId());
        assertEquals("P201", created.getProviderId());
        assertEquals("M501", created.getRecipientId());
        assertEquals(RecipientType.MEMBER, created.getRecipientType());
        assertEquals("key-1", created.getIdempotencyKey());
        assertEquals("hash-1", created.getRequestHash());
    }

    @Test
    void cashlessRecipientComesFromTheVerifiedClaim() {
        request.setSettlementType(SettlementType.CASHLESS);
        when(fingerprint.calculate(request)).thenReturn("hash-2");
        when(idempotencyLookup.find("key-2")).thenReturn(Optional.empty());
        when(claimClient.getClaimByBusinessId("CLM1001")).thenReturn(approvedClaim);
        when(creationTransaction.persist(any(Settlement.class), eq("operator"))).thenReturn(response);

        service.create(request, "key-2", "operator");

        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(creationTransaction).persist(captor.capture(), eq("operator"));
        assertEquals(RecipientType.PROVIDER, captor.getValue().getRecipientType());
        assertEquals("P201", captor.getValue().getRecipientId());
    }

    @Test
    void replaysSameKeyAndPayloadWithoutCallingClaimService() {
        when(fingerprint.calculate(request)).thenReturn("hash-1");
        when(idempotencyLookup.find("key-1")).thenReturn(Optional.of(
                new SettlementIdempotencyLookup.ExistingSettlement("hash-1", response)));

        assertSame(response, service.create(request, "key-1", "operator"));
        verify(claimClient, never()).getClaimByBusinessId(any());
        verify(creationTransaction, never()).persist(any(), any());
    }

    @Test
    void rejectsSameKeyWithDifferentPayload() {
        when(fingerprint.calculate(request)).thenReturn("new-hash");
        when(idempotencyLookup.find("key-1")).thenReturn(Optional.of(
                new SettlementIdempotencyLookup.ExistingSettlement("existing-hash", response)));

        assertThrows(SettlementException.class, () -> service.create(request, "key-1", "operator"));
        verify(claimClient, never()).getClaimByBusinessId(any());
    }

    @Test
    void resolvesConcurrentIdenticalRequestsByReturningWinningResult() {
        when(fingerprint.calculate(request)).thenReturn("hash-1");
        when(idempotencyLookup.find("key-1")).thenReturn(Optional.empty(), Optional.of(
                new SettlementIdempotencyLookup.ExistingSettlement("hash-1", response)));
        when(claimClient.getClaimByBusinessId("CLM1001")).thenReturn(approvedClaim);
        when(creationTransaction.persist(any(Settlement.class), eq("operator")))
                .thenThrow(new DataIntegrityViolationException("duplicate idempotency key"));

        assertSame(response, service.create(request, "key-1", "operator"));
    }

    @Test
    void rejectsUnapprovedClaim() {
        approvedClaim.setClaimStatuses("PENDING_DOCUMENTS");
        when(fingerprint.calculate(request)).thenReturn("hash-1");
        when(idempotencyLookup.find("key-1")).thenReturn(Optional.empty());
        when(claimClient.getClaimByBusinessId("CLM1001")).thenReturn(approvedClaim);

        assertThrows(SettlementException.class, () -> service.create(request, "key-1", "operator"));
        verify(creationTransaction, never()).persist(any(), any());
    }

    @Test
    void reportsClaimServiceFailuresAsTechnicalFailures() {
        FeignException unavailable = org.mockito.Mockito.mock(FeignException.class);
        when(fingerprint.calculate(request)).thenReturn("hash-1");
        when(idempotencyLookup.find("key-1")).thenReturn(Optional.empty());
        when(claimClient.getClaimByBusinessId("CLM1001")).thenThrow(unavailable);

        assertThrows(ClaimServiceUnavailableException.class,
                () -> service.create(request, "key-1", "operator"));
    }

    @Test
    void transitionsToSettledAndStoresPaymentDataSeparately() {
        Settlement settlement = pendingSettlement();
        settlement.setSettlementStatus(SettlementStatus.PAYMENT_PROCESSING);
        settlement.setId(7L);
        when(settlementRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(settlement));
        when(paymentRepository.findByPaymentReference("PAY10001")).thenReturn(Optional.empty());
        when(settlementRepository.save(settlement)).thenReturn(settlement);
        when(mapper.toResponse(settlement)).thenReturn(response);
        PaymentStatusRequest paymentRequest = new PaymentStatusRequest();
        paymentRequest.setStatus(SettlementStatus.SETTLED);
        paymentRequest.setPaymentReference("PAY10001");
        paymentRequest.setRemarks("Payment completed");

        service.updatePaymentStatus(7L, paymentRequest, "payment-service");

        assertEquals(SettlementStatus.SETTLED, settlement.getSettlementStatus());
        assertEquals(1, settlement.getPaymentTransactions().size());
        SettlementPaymentTransaction payment = settlement.getPaymentTransactions().get(0);
        assertEquals(PaymentTransactionStatus.SUCCEEDED, payment.getTransactionStatus());
        assertEquals("PAY10001", payment.getPaymentReference());
        assertEquals(new BigDecimal("105000.00"), payment.getAmount());
        verify(auditRepository).save(any(SettlementAudit.class));
        verify(paymentRepository).save(payment);
    }

    @Test
    void usesPessimisticLockForLifecycleUpdatesAndRejectsInvalidTransitions() {
        Settlement settlement = pendingSettlement();
        settlement.setId(7L);
        when(settlementRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(settlement));
        PaymentStatusRequest paymentRequest = new PaymentStatusRequest();
        paymentRequest.setStatus(SettlementStatus.SETTLED);
        paymentRequest.setPaymentReference("PAY10001");

        assertThrows(SettlementException.class,
                () -> service.updatePaymentStatus(7L, paymentRequest, "payment-service"));
        verify(settlementRepository).findByIdForUpdate(7L);
        verify(auditRepository, never()).save(any());
    }

    private Settlement pendingSettlement() {
        Settlement settlement = new Settlement();
        settlement.setIdempotencyKey("key");
        settlement.setRequestHash("hash");
        settlement.setClaimId("CLM1001");
        settlement.setMemberId("M501");
        settlement.setPolicyId(1001L);
        settlement.setSettlementType(SettlementType.REIMBURSEMENT);
        settlement.setApprovedClaimAmount(new BigDecimal("120000.00"));
        settlement.setDeductibleAmount(new BigDecimal("5000.00"));
        settlement.setCopayAmount(new BigDecimal("10000.00"));
        settlement.setNonPayableAmount(BigDecimal.ZERO.setScale(2));
        settlement.setPayableAmount(new BigDecimal("105000.00"));
        settlement.setSettlementStatus(SettlementStatus.PENDING);
        settlement.setRecipientType(RecipientType.MEMBER);
        settlement.setRecipientId("M501");
        settlement.setCurrency("INR");
        settlement.setBreakdown(new ArrayList<>());
        settlement.setAuditHistory(new ArrayList<>());
        settlement.setPaymentTransactions(new ArrayList<>());
        return settlement;
    }
}
