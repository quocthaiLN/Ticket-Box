package com.ticketbox.api.module.order.domain.mappers;

import com.ticketbox.api.module.order.domain.dtos.OrderItemResponse;
import com.ticketbox.api.module.order.domain.dtos.OrderResponse;
import com.ticketbox.api.module.order.domain.entities.Order;
import com.ticketbox.api.module.order.domain.entities.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "orderId", source = "id")
    @Mapping(target = "concertId", source = "concert.id")
    @Mapping(target = "status", expression = "java(order.getStatus() != null ? order.getStatus().name() : null)")
    @Mapping(target = "items", source = "orderItems")
    OrderResponse toResponse(Order order);

    @Mapping(target = "ticketTypeId", source = "ticketType.id")
    OrderItemResponse toItemResponse(OrderItem item);
}
