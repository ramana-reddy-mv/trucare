package com.trucare.payment.mapper;

import com.trucare.payment.dto.*;
import com.trucare.payment.model.*;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    PaymentResponse toResponse(Payment payment);
    PaymentTransactionResponse toResponse(PaymentTransaction transaction);
    PaymentAuditResponse toResponse(PaymentAudit audit);
    CallbackDeliveryResponse toResponse(CallbackDelivery delivery);
}
