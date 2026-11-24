package com.trucare.claims.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

public class ClaimAmountValidator implements ConstraintValidator<ValidClaimAmount, BigDecimal> {
    private static final int MAX_SCALE = 2;
    private static final int MAX_INTEGER_DIGITS = 17;

    @Override
    public boolean isValid(BigDecimal amount, ConstraintValidatorContext context) {
        if (amount == null) {
            return true; // Requiredness is expressed independently with @NotNull.
        }
        if (amount.signum() <= 0) {
            return false;
        }

        BigDecimal normalized = amount.stripTrailingZeros();
        int scale = Math.max(normalized.scale(), 0);
        int integerDigits = Math.max(normalized.precision() - normalized.scale(), 0);
        return scale <= MAX_SCALE && integerDigits <= MAX_INTEGER_DIGITS;
    }
}
