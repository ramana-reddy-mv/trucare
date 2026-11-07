package com.trucare.payment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "settlement-service", contextId = "paymentSettlementClient",
        configuration = SettlementClientConfiguration.class)
public interface SettlementClient {
    @GetMapping("/api/settlements/{settlementId}")
    SettlementResponse getSettlement(@PathVariable("settlementId") Long settlementId);

    @PostMapping("/api/settlements/{settlementId}/payment-status")
    SettlementResponse updatePaymentStatus(@PathVariable("settlementId") Long settlementId,
                                            @RequestBody SettlementPaymentStatusRequest request);
}
