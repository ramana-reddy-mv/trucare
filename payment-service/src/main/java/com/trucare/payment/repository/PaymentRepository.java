package com.trucare.payment.repository;

import com.trucare.payment.model.Payment;
import com.trucare.payment.model.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {
    Optional<Payment> findByPaymentReference(String paymentReference);
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
    Optional<Payment> findBySettlementId(Long settlementId);
    java.util.Optional<Payment> findFirstByPaymentStatusOrderByCreatedAtAsc(PaymentStatus status);
    Page<Payment> findAll(Specification<Payment> specification, Pageable pageable);
    Page<Payment> findByPaymentStatus(PaymentStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);
}
