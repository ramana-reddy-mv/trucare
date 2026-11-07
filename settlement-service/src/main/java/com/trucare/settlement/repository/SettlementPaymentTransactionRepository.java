package com.trucare.settlement.repository;

import com.trucare.settlement.model.SettlementPaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementPaymentTransactionRepository
        extends JpaRepository<SettlementPaymentTransaction, Long> {

    Optional<SettlementPaymentTransaction> findByPaymentReference(String paymentReference);
}
