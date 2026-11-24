package com.trucare.claims.validation;

import com.trucare.claims.dto.CreateClaimRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimAmountValidatorTest {
    private static Validator validator;
    private static AutoCloseable validatorFactory;

    @BeforeAll
    static void createValidator() {
        var factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
        validatorFactory = factory::close;
    }

    @AfterAll
    static void closeValidator() throws Exception {
        validatorFactory.close();
    }

    @Test
    void acceptsPositiveAmountWithinDatabasePrecision() {
        assertTrue(amountIsValid("123.45"));
        assertTrue(amountIsValid("99999999999999999.99"));
    }

    @Test
    void validCreateRequestHasNoValidationErrors() {
        assertTrue(validator.validate(validRequest()).isEmpty());
    }

    @Test
    void rejectsMissingRequiredIdentifiersTypeAndDate() {
        CreateClaimRequest request = validRequest();
        request.setMemberId(" ");
        request.setProviderId(null);
        request.setPolicyNumber("");
        request.setTreatmentType(null);
        request.setTreatmentDate(null);
        var fields = validator.validate(request).stream()
                .map(error -> error.getPropertyPath().toString()).collect(java.util.stream.Collectors.toSet());
        assertTrue(fields.containsAll(java.util.Set.of("memberId", "providerId", "policyNumber",
                "treatmentType", "treatmentDate")));
    }

    @Test
    void rejectsZeroAndNegativeAmounts() {
        assertFalse(amountIsValid("0"));
        assertFalse(amountIsValid("-0.01"));
    }

    @Test
    void rejectsNullAsRequiredByRequestContract() {
        CreateClaimRequest request = validRequest();
        request.setClaimAmount(null);
        assertTrue(validator.validate(request).stream()
                .anyMatch(error -> error.getPropertyPath().toString().equals("claimAmount")));
    }

    @Test
    void rejectsAmountWithMoreThanTwoFractionalDigitsOrExcessPrecision() {
        assertFalse(amountIsValid("1.001"));
        assertFalse(amountIsValid("100000000000000000.00"));
    }

    private boolean amountIsValid(String amount) {
        CreateClaimRequest request = validRequest();
        request.setClaimAmount(new BigDecimal(amount));
        return validator.validate(request).stream()
                .noneMatch(error -> error.getPropertyPath().toString().equals("claimAmount"));
    }

    private CreateClaimRequest validRequest() {
        CreateClaimRequest request = new CreateClaimRequest();
        request.setMemberId("M-1");
        request.setProviderId("P-1");
        request.setPolicyNumber("POL-1");
        request.setTreatmentType("HOSPITALIZATION");
        request.setTreatmentDate(LocalDate.of(2026, 1, 1));
        request.setClaimAmount(new BigDecimal("1.00"));
        return request;
    }
}
