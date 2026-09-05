package com.ticketbox.api.module.order.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.infrastructure.response.ApiResponse;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.order.domain.dtos.CreateOrderItemRequest;
import com.ticketbox.api.module.order.domain.dtos.CreateOrderRequest;
import com.ticketbox.api.module.order.domain.dtos.HeldOrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderIdempotencyServiceTest {

    private static final UUID USER_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID CONCERT_ID = UUID.fromString("11111111-1111-1111-8111-111111111111");
    private static final UUID TICKET_TYPE_ID = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID IDEMPOTENCY_KEY = UUID.fromString("c41e9a00-1111-4111-8111-111111111111");

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private User user;
    private OrderIdempotencyService idempotencyService;

    @BeforeEach
    void setUp() {
        user = User.builder().id(USER_ID).email("audience@example.com").fullName("Audience").build();
        idempotencyService = new OrderIdempotencyService(stringRedisTemplate, objectMapper, orderService);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Replays the cached held order without starting a second hold")
    void createOrReplay_replaysCompletedResponse() throws Exception {
        CreateOrderRequest request = request(1);
        when(valueOperations.get(anyString())).thenReturn(completedSuccessRecord(request));

        ApiResponse<HeldOrderResponse> response = idempotencyService.createOrReplay(user, IDEMPOTENCY_KEY, request);

        assertEquals("HELD", response.getData().getStatus());
        assertEquals(CONCERT_ID, response.getData().getConcertId());
        verifyNoInteractions(orderService);
    }

    @Test
    @DisplayName("Rejects a different payload that reuses an existing UUID")
    void createOrReplay_rejectsDifferentPayloadForExistingKey() throws Exception {
        when(valueOperations.get(anyString())).thenReturn(completedSuccessRecord(request(1)));

        AppException exception = assertThrows(AppException.class,
                () -> idempotencyService.createOrReplay(user, IDEMPOTENCY_KEY, request(2)));

        assertEquals("IDEMPOTENCY_KEY_REUSED", exception.getErrorCode());
        verifyNoInteractions(orderService);
    }

    private CreateOrderRequest request(int quantity) {
        return CreateOrderRequest.builder()
                .concertId(CONCERT_ID)
                .items(List.of(CreateOrderItemRequest.builder().ticketTypeId(TICKET_TYPE_ID).quantity(quantity).build()))
                .build();
    }

    private String completedSuccessRecord(CreateOrderRequest request) throws Exception {
        ObjectNode record = objectMapper.createObjectNode();
        record.put("state", "COMPLETED");
        record.put("outcome", "SUCCESS");
        record.put("fingerprint", fingerprint(request));
        record.set("response", objectMapper.valueToTree(ApiResponse.success(HeldOrderResponse.builder()
                .orderId(UUID.fromString("99999999-9999-4999-8999-999999999999"))
                .concertId(CONCERT_ID)
                .status("HELD")
                .build())));
        return objectMapper.writeValueAsString(record);
    }

    private String fingerprint(CreateOrderRequest request) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        String payload = USER_ID + "|" + request.getConcertId() + "|"
                + request.getItems().stream()
                .sorted(java.util.Comparator.comparing(item -> item.getTicketTypeId().toString()))
                .map(item -> item.getTicketTypeId() + ":" + item.getQuantity())
                .reduce("", (left, right) -> left + "|" + right);
        return java.util.HexFormat.of().formatHex(digest.digest(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
