package com.trucare.settlement.mapper;

import com.trucare.settlement.dto.SettlementAuditResponse;
import com.trucare.settlement.dto.SettlementBreakdownResponse;
import com.trucare.settlement.dto.SettlementResponse;
import com.trucare.settlement.dto.SettlementPaymentResponse;
import com.trucare.settlement.model.Settlement;
import com.trucare.settlement.model.SettlementAudit;
import com.trucare.settlement.model.SettlementBreakdown;
import com.trucare.settlement.model.SettlementPaymentTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface SettlementMapper {

    SettlementResponse toResponse(Settlement settlement);

    @Mapping(target = "breakdown", expression = "java(java.util.List.of())")
    @Mapping(target = "auditHistory", expression = "java(java.util.List.of())")
    @Mapping(target = "paymentTransactions", expression = "java(java.util.List.of())")
    SettlementResponse toSummaryResponse(Settlement settlement);

    SettlementBreakdownResponse toResponse(SettlementBreakdown breakdown);

    SettlementAuditResponse toResponse(SettlementAudit audit);

    SettlementPaymentResponse toResponse(SettlementPaymentTransaction transaction);
}
