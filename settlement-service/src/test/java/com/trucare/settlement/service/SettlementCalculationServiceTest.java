package com.trucare.settlement.service;

import com.trucare.settlement.exception.SettlementException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettlementCalculationServiceTest {

    private final SettlementCalculationService service = new SettlementCalculationService();

    @Test
    void calculatesPayableAmountWithTwoDecimalPlaces() {
        BigDecimal payable = service.calculate(
                new BigDecimal("120000.00"),
                new BigDecimal("5000.00"),
                new BigDecimal("10000.00"),
                new BigDecimal("0.00"));

        assertEquals(new BigDecimal("105000.00"), payable);
    }

    @Test
    void roundsAllInputsUsingHalfUp() {
        assertEquals(new BigDecimal("9.99"), service.calculate(
                new BigDecimal("10.004"), new BigDecimal("0.005"),
                new BigDecimal("0"), new BigDecimal("0")));
    }

    @Test
    void rejectsNegativeAmounts() {
        assertThrows(SettlementException.class, () -> service.calculate(
                new BigDecimal("10"), new BigDecimal("-0.01"),
                BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void rejectsDeductionsExceedingApprovedAmount() {
        assertThrows(SettlementException.class, () -> service.calculate(
                new BigDecimal("10"), new BigDecimal("5"),
                new BigDecimal("4"), new BigDecimal("2")));
    }
}
