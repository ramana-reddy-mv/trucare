package com.trucare.payment.gateway;

import com.trucare.payment.model.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payment.gateway.mode", havingValue = "MOCK", matchIfMissing = true)
public class MockPaymentGateway implements PaymentGateway {

    private final GatewayOutcome defaultOutcome;

    public MockPaymentGateway(@Value("${payment.gateway.mock.default-result:SUCCESS}") GatewayOutcome defaultOutcome) {
        this.defaultOutcome = defaultOutcome;
    }

    @Override
    public PaymentGatewayResult process(Payment payment, String transactionReference,
                                        String providerIdempotencyKey) {
        return result(defaultOutcome, "MOCK-" + transactionReference);
    }

    @Override
    public PaymentGatewayResult query(String providerReference) {
        return result(defaultOutcome, providerReference);
    }

    private PaymentGatewayResult result(GatewayOutcome outcome, String reference) {
        return switch (outcome) {
            case SUCCESS -> new PaymentGatewayResult(outcome, reference, null, null);
            case FAILED -> new PaymentGatewayResult(outcome, reference,
                    "MOCK_DECLINED", "Mock gateway returned a definitive decline");
            case TIMEOUT -> new PaymentGatewayResult(outcome, reference,
                    "MOCK_TIMEOUT", "Mock gateway outcome is unknown after timeout");
            case PROCESSING -> new PaymentGatewayResult(outcome, reference, null,
                    "Mock gateway is still processing the transaction");
        };
    }
}
