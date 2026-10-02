package com.ticketbox.api.module.order.domain.exception;

import com.ticketbox.api.module.shared.exception.ConflictException;
import java.util.Map;

public final class OrderNotSettlableException extends ConflictException {
    public OrderNotSettlableException() {
        super(OrderErrorCode.ORDER_NOT_SETTLABLE, "Order is not eligible for payment settlement", Map.of());
    }
}
