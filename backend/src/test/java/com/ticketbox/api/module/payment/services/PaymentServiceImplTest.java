package com.ticketbox.api.module.payment.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserRole;
import com.ticketbox.api.module.order.domain.entities.Order;
import com.ticketbox.api.module.payment.domain.dtos.PaymentResponse;
import com.ticketbox.api.module.payment.domain.entities.Payment;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import com.ticketbox.api.module.payment.repositories.PaymentRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private com.ticketbox.api.module.order.repositories.OrderRepository orderRepository;

    @Mock
    private com.ticketbox.api.module.shared.idempotency.IdempotencyService idempotencyService;

    @Mock
    private com.ticketbox.api.module.order.services.OrderInventoryService orderInventoryService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    private final UUID paymentId = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private final User owner = user("22222222-2222-4222-8222-222222222222");
    private final User anotherUser = user("33333333-3333-4333-8333-333333333333");

    @Test
    void getPayment_returnsAllowlistedPaymentForOwner() {
        Payment payment = payment(owner);
        when(paymentRepository.findPaymentWithOrderAndUserById(paymentId)).thenReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPayment(owner, paymentId);

        assertEquals(paymentId, response.paymentId());
        assertEquals("MOMO", response.provider());
        assertEquals("SUCCEEDED", response.status());
        assertEquals(payment.getPaidAt(), response.paidAt());
        assertEquals(payment.getFailureReason(), response.failureReason());
        verify(paymentRepository).findPaymentWithOrderAndUserById(paymentId);
    }

    @Test
    void getPayment_hidesPaymentOwnedByAnotherUser() {
        when(paymentRepository.findPaymentWithOrderAndUserById(paymentId)).thenReturn(Optional.of(payment(owner)));

        AppException exception = assertThrows(AppException.class,
                () -> paymentService.getPayment(anotherUser, paymentId));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
        assertEquals(404, exception.getStatus().value());
    }

    @Test
    void getPayment_returnsNotFoundWhenPaymentDoesNotExist() {
        when(paymentRepository.findPaymentWithOrderAndUserById(paymentId)).thenReturn(Optional.empty());

        AppException exception = assertThrows(AppException.class,
                () -> paymentService.getPayment(owner, paymentId));

        assertEquals("PAYMENT_NOT_FOUND", exception.getErrorCode());
    }

    private static User user(String id) {
        return User.builder()
                .id(UUID.fromString(id))
                .email(id + "@example.com")
                .fullName("Audience")
                .role(UserRole.AUDIENCE)
                .build();
    }

    private Payment payment(User paymentOwner) {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 17, 15, 15);
        Order order = Order.builder()
                .id(UUID.fromString("44444444-4444-4444-8444-444444444444"))
                .user(paymentOwner)
                .holdExpiresAt(createdAt.plusMinutes(15))
                .build();
        return Payment.builder()
                .id(paymentId)
                .order(order)
                .provider(PaymentProvider.MOMO)
                .idempotencyKey("55555555-5555-4555-8555-555555555555")
                .amount(new BigDecimal("350000.00"))
                .currency("VND")
                .status(PaymentStatus.SUCCEEDED)
                .checkoutUrl("https://example.invalid/checkout")
                .paidAt(createdAt.plusMinutes(1))
                .failureReason("provider response retained for display")
                .createdAt(createdAt)
                .updatedAt(createdAt.plusMinutes(1))
                .build();
    }
}
