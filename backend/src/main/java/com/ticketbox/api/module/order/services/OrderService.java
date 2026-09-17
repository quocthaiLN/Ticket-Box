package com.ticketbox.api.module.order.services;

import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.order.domain.dtos.CreateOrderRequest;
import com.ticketbox.api.module.order.domain.dtos.OrderResponse;

import java.util.Optional;
import java.util.UUID;

public interface OrderService {

    OrderResponse createHeldOrder(User currentUser, String idempotencyKey, CreateOrderRequest request);

    Optional<OrderResponse> findExistingOrder(User currentUser, String idempotencyKey, CreateOrderRequest request);

    OrderResponse getOrder(User currentUser, UUID orderId);
}
