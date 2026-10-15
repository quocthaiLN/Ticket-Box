package com.ticketbox.api.module.payment.domain.mappers;

import com.ticketbox.api.module.payment.domain.dtos.RefundResponse;
import com.ticketbox.api.module.payment.domain.entities.Refund;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface RefundMapper {

    @Mapping(target = "paymentId", source = "payment.id")
    RefundResponse toResponse(Refund refund);
}
