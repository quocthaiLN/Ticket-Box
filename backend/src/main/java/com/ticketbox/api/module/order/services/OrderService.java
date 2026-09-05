package com.ticketbox.api.module.order.services;

import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.order.domain.dtos.CreateOrderRequest;
import com.ticketbox.api.module.order.domain.dtos.HeldOrderResponse;

import java.util.Optional;

public interface OrderService {

    HeldOrderResponse createHeldOrder(User currentUser, String idempotencyKey, CreateOrderRequest request);

    Optional<HeldOrderResponse> findExistingOrder(User currentUser, String idempotencyKey, CreateOrderRequest request);
}
