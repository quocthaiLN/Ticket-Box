package com.ticketbox.api.module.order.domain.exception;

import com.ticketbox.api.module.shared.exception.ForbiddenException;
import java.util.Map;

public final class OrderAccessDeniedException extends ForbiddenException {
    public OrderAccessDeniedException() {
        super(OrderErrorCode.ORDER_ACCESS_DENIED, "You do not have access to this order", Map.of());
    }
}
