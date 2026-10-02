package com.ticketbox.api.module.order.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.infrastructure.exception.GlobalExceptionHandler;
import com.ticketbox.api.module.order.domain.dtos.CreateOrderItemRequest;
import com.ticketbox.api.module.order.domain.dtos.CreateOrderRequest;
import com.ticketbox.api.module.order.services.OrderService;
import com.ticketbox.api.module.payment.services.PaymentService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class OrderControllerTest {
    @Mock private OrderService orderService;
    @Mock private PaymentService paymentService;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        mockMvc = MockMvcBuilders.standaloneSetup(new OrderController(orderService, paymentService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void createOrder_missingIdempotencyHeaderIsValidation400() throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .concertId(UUID.randomUUID())
                .items(List.of(CreateOrderItemRequest.builder()
                        .ticketTypeId(UUID.randomUUID()).quantity(1).build()))
                .build();

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_IDEMPOTENCY_KEY"));
    }
}
