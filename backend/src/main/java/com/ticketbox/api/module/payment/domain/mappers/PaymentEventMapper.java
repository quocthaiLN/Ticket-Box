package com.ticketbox.api.module.payment.domain.mappers;

import com.ticketbox.api.module.payment.domain.dtos.PaymentEventResponse;
import com.ticketbox.api.module.payment.domain.entities.PaymentEvent;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentEventMapper {

    @Mapping(target = "paymentId", source = "payment.id")
    PaymentEventResponse toResponse(PaymentEvent event);
}
