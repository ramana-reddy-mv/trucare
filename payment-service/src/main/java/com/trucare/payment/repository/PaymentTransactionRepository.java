package com.trucare.payment.repository;

import com.trucare.payment.model.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    Optional<PaymentTransaction> findTopByPaymentIdOrderByAttemptNumberDesc(Long paymentId);
}
