package com.trucare.settlement.service;

import com.trucare.settlement.exception.SettlementException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class SettlementCalculationService {

    public BigDecimal calculate(BigDecimal approved, BigDecimal deductible,
                                BigDecimal copay, BigDecimal nonPayable) {
        BigDecimal normalizedApproved = normalize(approved, "approvedClaimAmount");
        BigDecimal normalizedDeductible = normalize(deductible, "deductibleAmount");
        BigDecimal normalizedCopay = normalize(copay, "copayAmount");
        BigDecimal normalizedNonPayable = normalize(nonPayable, "nonPayableAmount");

        BigDecimal deductions = normalizedDeductible.add(normalizedCopay).add(normalizedNonPayable);
        if (deductions.compareTo(normalizedApproved) > 0) {
            throw new SettlementException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Deductible, co-payment, and non-payable amounts cannot exceed the approved amount");
        }
        return normalizedApproved.subtract(deductions).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal normalize(BigDecimal amount, String field) {
        if (amount == null || amount.signum() < 0) {
            throw new SettlementException(HttpStatus.BAD_REQUEST, field + " must be non-negative");
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        if (normalized.precision() - normalized.scale() > 17) {
            throw new SettlementException(HttpStatus.BAD_REQUEST, field + " exceeds the supported precision");
        }
        return normalized;
    }
}
