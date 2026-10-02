package com.ticketbox.api.module.order.domain.exception;

import com.ticketbox.api.module.shared.exception.NotFoundException;
import java.util.Map;

public final class OrderNotFoundException extends NotFoundException {
    public OrderNotFoundException() { super(OrderErrorCode.ORDER_NOT_FOUND, "Order not found", Map.of()); }
}
