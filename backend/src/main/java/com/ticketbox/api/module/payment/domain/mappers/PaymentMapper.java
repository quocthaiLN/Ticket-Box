package com.ticketbox.api.module.payment.domain.mappers;

import com.ticketbox.api.module.payment.domain.dtos.CreatePaymentResponse;
import com.ticketbox.api.module.payment.domain.dtos.PaymentResponse;
import com.ticketbox.api.module.payment.domain.entities.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "paymentId", source = "id")
    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "holdExpiresAt", source = "order.holdExpiresAt")
    CreatePaymentResponse toCreatePaymentResponse(Payment payment);

    @Mapping(target = "paymentId", source = "id")
    @Mapping(target = "orderId", source = "order.id")
    @Mapping(target = "holdExpiresAt", source = "order.holdExpiresAt")
    PaymentResponse toPaymentResponse(Payment payment);
}
