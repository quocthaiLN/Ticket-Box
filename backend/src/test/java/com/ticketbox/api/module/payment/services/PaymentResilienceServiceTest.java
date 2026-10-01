package com.ticketbox.api.module.payment.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.order.domain.entities.Order;
import com.ticketbox.api.module.order.domain.entities.OrderStatus;
import com.ticketbox.api.module.order.repositories.OrderRepository;
import com.ticketbox.api.module.order.services.OrderInventoryService;
import com.ticketbox.api.module.payment.domain.dtos.CallbackHandlingResult;
import com.ticketbox.api.module.payment.domain.dtos.CreatePaymentRequest;
import com.ticketbox.api.module.payment.domain.dtos.CreatePaymentResponse;
import com.ticketbox.api.module.payment.domain.dtos.GatewayCallback;
import com.ticketbox.api.module.payment.domain.dtos.PaymentGatewayRequest;
import com.ticketbox.api.module.payment.domain.entities.Payment;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import com.ticketbox.api.module.payment.events.PaymentCompletedEvent;
import com.ticketbox.api.module.payment.gateways.GatewayQueryResult;
import com.ticketbox.api.module.payment.gateways.MomoGateway;
import com.ticketbox.api.module.payment.gateways.PaymentGatewayStrategy;
import com.ticketbox.api.module.payment.gateways.PaymentStatusQueryGateway;
import com.ticketbox.api.module.payment.repositories.PaymentRepository;
import com.ticketbox.api.module.shared.idempotency.IdempotencyService;
import com.ticketbox.api.module.ticket.consumers.TicketPaymentSucceededConsumer;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.ResourceAccessException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)

class PaymentResilienceServiceTest {
    private static final UUID PAYMENT_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID ORDER_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final BigDecimal AMOUNT = new BigDecimal("1000.00");

    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentGatewayStrategy momoGateway;
    @Mock private PaymentStatusQueryGateway queryGateway;
    @Mock private IdempotencyService idempotencyService;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private TransactionStatus transactionStatus;
    @Mock private OrderInventoryService orderInventoryService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private TicketPaymentSucceededConsumer ticketIssuer;

    private PaymentServiceImpl service;
    private User user;
    private Order order;
    private AtomicReference<Payment> stored;

    @BeforeEach
    void setUp() {
        user = User.builder().id(UUID.randomUUID()).build();
        order = Order.builder().id(ORDER_ID).user(user).status(OrderStatus.HELD)
                .holdExpiresAt(LocalDateTime.now().plusMinutes(10)).totalAmount(AMOUNT).currency("VND").build();
        stored = new AtomicReference<>();
        service = new PaymentServiceImpl(orderRepository, paymentRepository, List.of(momoGateway),
                List.of(queryGateway), idempotencyService, transactionTemplate, orderInventoryService,
                eventPublisher, new ObjectMapper(), ticketIssuer);
        lenient().when(momoGateway.getProvider()).thenReturn(PaymentProvider.MOMO);
        lenient().when(momoGateway.transactionReference(PAYMENT_ID)).thenReturn(PAYMENT_ID.toString());
        lenient().when(queryGateway.getProvider()).thenReturn(PaymentProvider.MOMO);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
        lenient().when(paymentRepository.saveAndFlush(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            if (payment.getId() == null) {
                payment.setId(PAYMENT_ID);
            }
            stored.set(payment);
            return payment;
        });
        lenient().when(paymentRepository.findByIdForUpdate(PAYMENT_ID))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        lenient().when(paymentRepository.findPaymentWithOrderAndUserById(PAYMENT_ID))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        lenient().when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
    }

    @Test
    void momoTimeoutKeepsCreatingAndSchedulesRecovery() {
        stubCreateAction();
        when(momoGateway.createPaymentUrl(any())).thenThrow(new MomoGateway.CreateOutcomeUnknownException("timeout"));

        CreatePaymentResponse response = service.createPayment(user, UUID.randomUUID().toString(),
                createRequest(), "127.0.0.1");

        assertThat(response.status()).isEqualTo("CREATING");
        assertThat(response.checkoutUrl()).isNull();
        assertThat(stored.get().getNextReconcileAt()).isAfter(LocalDateTime.now());
        verify(paymentRepository).existsByOrderIdAndStatus(ORDER_ID, PaymentStatus.CREATING);
        ArgumentCaptor<PaymentGatewayRequest> request = ArgumentCaptor.forClass(PaymentGatewayRequest.class);
        verify(momoGateway).createPaymentUrl(request.capture());
        assertThat(request.getValue().txnRef()).isEqualTo(PAYMENT_ID.toString());
    }

    @Test
    void momoDefinitiveRejectionMarksPaymentFailed() {
        stubCreateAction();
        when(momoGateway.createPaymentUrl(any())).thenThrow(new MomoGateway.CreateRejectedException("resultCode=1002"));

        CreatePaymentResponse response = service.createPayment(user, UUID.randomUUID().toString(),
                createRequest(), "127.0.0.1");

        assertThat(response.status()).isEqualTo("FAILED");
        assertThat(stored.get().getNextReconcileAt()).isNull();
        assertThat(response.checkoutUrl()).isNull();
    }

    @Test
    void retryWorkerUsesSameReferenceAndMovesToPendingOnlyWithUrl() {
        Payment payment = pendingPayment(PaymentStatus.CREATING);
        payment.setNextReconcileAt(LocalDateTime.now().minusSeconds(1));
        when(momoGateway.createPaymentUrl(any())).thenReturn("https://momo.test/pay");

        service.reconcilePayment(PAYMENT_ID);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getCheckoutUrl()).isEqualTo("https://momo.test/pay");
        ArgumentCaptor<PaymentGatewayRequest> request = ArgumentCaptor.forClass(PaymentGatewayRequest.class);
        verify(momoGateway).createPaymentUrl(request.capture());
        assertThat(request.getValue().txnRef()).isEqualTo(PAYMENT_ID.toString());
    }

    @Test
    void invalidIpnDoesNotTouchPayment() {
        when(momoGateway.verifyCallback(anyMap())).thenReturn(callback(false, AMOUNT, PaymentStatus.SUCCEEDED));

        assertThat(service.handleCallbackResult(PaymentProvider.MOMO, Map.of()))
                .isEqualTo(CallbackHandlingResult.INVALID_SIGNATURE);
        verify(paymentRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void amountMismatchDoesNotAckOrSettle() {
        pendingPayment(PaymentStatus.PENDING);
        when(momoGateway.verifyCallback(anyMap()))
                .thenReturn(callback(true, new BigDecimal("2000"), PaymentStatus.SUCCEEDED));

        assertThat(service.handleCallbackResult(PaymentProvider.MOMO, Map.of()))
                .isEqualTo(CallbackHandlingResult.AMOUNT_MISMATCH);
        verify(orderInventoryService, never()).settlePaidOrder(any(), any());
        verify(ticketIssuer, never()).issueTickets(any());
    }

    @Test
    void duplicateSuccessIssuesTicketsOnce() {
        Payment payment = pendingPayment(PaymentStatus.PENDING);
        when(momoGateway.verifyCallback(anyMap())).thenReturn(callback(true, AMOUNT, PaymentStatus.SUCCEEDED));

        assertThat(service.handleCallbackResult(PaymentProvider.MOMO, Map.of()))
                .isEqualTo(CallbackHandlingResult.PROCESSED);
        assertThat(service.handleCallbackResult(PaymentProvider.MOMO, Map.of()))
                .isEqualTo(CallbackHandlingResult.ALREADY_PROCESSED);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.isRefundRequired()).isFalse();
        verify(orderInventoryService, times(1)).settlePaidOrder(eq(ORDER_ID), any());
        verify(ticketIssuer, times(1)).issueTickets(any());
    }

    @Test
    void ticketIssuanceFailurePropagatesSoIpnIsNotAcknowledged() {
        pendingPayment(PaymentStatus.PENDING);
        when(momoGateway.verifyCallback(anyMap())).thenReturn(callback(true, AMOUNT, PaymentStatus.SUCCEEDED));
        doThrow(new IllegalStateException("ticket insert failed")).when(ticketIssuer).issueTickets(any());

        assertThatThrownBy(() -> service.handleCallbackResult(PaymentProvider.MOMO, Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ticket insert failed");
    }

    @Test
    void lateSuccessRecordsMoneyWithoutIssuingTickets() {
        Payment payment = pendingPayment(PaymentStatus.PENDING);
        order.setHoldExpiresAt(LocalDateTime.now().minusSeconds(1));
        when(momoGateway.verifyCallback(anyMap())).thenReturn(callback(true, AMOUNT, PaymentStatus.SUCCEEDED));

        assertThat(service.handleCallbackResult(PaymentProvider.MOMO, Map.of()))
                .isEqualTo(CallbackHandlingResult.PROCESSED);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.isRefundRequired()).isTrue();
        verify(orderInventoryService, never()).settlePaidOrder(any(), any());
        verify(ticketIssuer, never()).issueTickets(any());
    }

    @Test
    void queryTimeoutLeavesPendingAndSchedulesNextAttempt() {
        Payment payment = pendingPayment(PaymentStatus.PENDING);
        payment.setNextReconcileAt(LocalDateTime.now().minusSeconds(1));
        when(queryGateway.query(payment)).thenThrow(new ResourceAccessException("timeout"));

        service.reconcilePayment(PAYMENT_ID);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.getNextReconcileAt()).isAfter(LocalDateTime.now());
        verify(ticketIssuer, never()).issueTickets(any());
    }

    @Test
    void querySuccessAfterExpiryRequiresRefund() {
        Payment payment = pendingPayment(PaymentStatus.PENDING);
        payment.setNextReconcileAt(LocalDateTime.now().minusSeconds(1));
        order.setStatus(OrderStatus.EXPIRED);
        when(queryGateway.query(payment)).thenReturn(new GatewayQueryResult(PAYMENT_ID.toString(), AMOUNT,
                "12345", PaymentStatus.SUCCEEDED, "0"));

        service.reconcilePayment(PAYMENT_ID);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(payment.isRefundRequired()).isTrue();
        verify(ticketIssuer, never()).issueTickets(any());
    }

    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"CREATING", "PENDING"})
    void cancellationIsTerminalAndDoesNotChangeOrder(PaymentStatus initial) {
        Payment payment = pendingPayment(initial);
        payment.setNextReconcileAt(LocalDateTime.now().plusSeconds(30));
        var holdExpiry = order.getHoldExpiresAt();
        var cancelled = new GatewayCallback(true, Optional.of(PAYMENT_ID), Optional.of(AMOUNT), "12345",
                Optional.of(PaymentStatus.CANCELLED), Optional.of("MOMO resultCode=1006"), Map.of("resultCode", "1006"));
        when(momoGateway.verifyCallback(anyMap())).thenReturn(cancelled);
        assertThat(service.handleCallbackResult(PaymentProvider.MOMO, Map.of())).isEqualTo(CallbackHandlingResult.PROCESSED);
        assertThat(service.handleCallbackResult(PaymentProvider.MOMO, Map.of())).isEqualTo(CallbackHandlingResult.ALREADY_PROCESSED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.getFailureReason()).isEqualTo("MOMO resultCode=1006");
        assertThat(payment.getNextReconcileAt()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.HELD);
        assertThat(order.getHoldExpiresAt()).isEqualTo(holdExpiry);
        Mockito.verifyNoInteractions(orderInventoryService, ticketIssuer);
        var event = ArgumentCaptor.forClass(PaymentCompletedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().status()).isEqualTo(PaymentStatus.CANCELLED);
        when(momoGateway.verifyCallback(anyMap())).thenReturn(callback(true, AMOUNT, PaymentStatus.SUCCEEDED));
        assertThat(service.handleCallbackResult(PaymentProvider.MOMO, Map.of())).isEqualTo(CallbackHandlingResult.CONFLICTING_RESULT);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        Mockito.verifyNoMoreInteractions(eventPublisher);
    }

    @Test
    void queryCancellationThenIpnPublishesOnlyOnce() {
        Payment payment = pendingPayment(PaymentStatus.PENDING);
        payment.setNextReconcileAt(LocalDateTime.now().minusSeconds(1));
        when(queryGateway.query(payment)).thenReturn(new GatewayQueryResult(PAYMENT_ID.toString(), AMOUNT,
                "12345", PaymentStatus.CANCELLED, "1006"));
        service.reconcilePayment(PAYMENT_ID);
        when(momoGateway.verifyCallback(anyMap())).thenReturn(callback(true, AMOUNT, PaymentStatus.CANCELLED));
        assertThat(service.handleCallbackResult(PaymentProvider.MOMO, Map.of())).isEqualTo(CallbackHandlingResult.ALREADY_PROCESSED);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CANCELLED);
        assertThat(payment.getNextReconcileAt()).isNull();
        assertThat(payment.getFailureReason()).contains("1006");
        verify(eventPublisher, times(1)).publishEvent(any(PaymentCompletedEvent.class));
        Mockito.verifyNoInteractions(orderInventoryService, ticketIssuer);
    }

    @SuppressWarnings("unchecked")
    private void stubCreateAction() {
        when(orderRepository.findByIdForUpdate(ORDER_ID)).thenReturn(Optional.of(order));
        when(idempotencyService.execute(anyString(), anyString(), any(Duration.class),
                eq(CreatePaymentResponse.class), any(), any())).thenAnswer(invocation -> {
                    Supplier<CreatePaymentResponse> action = invocation.getArgument(5);
                    return action.get();
                });
    }

    private Payment pendingPayment(PaymentStatus status) {
        Payment payment = Payment.builder().id(PAYMENT_ID).order(order).provider(PaymentProvider.MOMO)
                .idempotencyKey(UUID.randomUUID().toString()).amount(AMOUNT).currency("VND")
                .status(status).checkoutUrl(status == PaymentStatus.PENDING ? "https://momo.test/pay" : null)
                .build();
        stored.set(payment);
        return payment;
    }

    private CreatePaymentRequest createRequest() {
        CreatePaymentRequest request = new CreatePaymentRequest();
        request.setOrderId(ORDER_ID);
        request.setProvider(PaymentProvider.MOMO);
        return request;
    }

    private GatewayCallback callback(boolean signatureValid, BigDecimal amount, PaymentStatus status) {
        return new GatewayCallback(signatureValid, Optional.of(PAYMENT_ID), Optional.of(amount), "12345",
                Optional.of(status), Optional.empty(), Map.of("resultCode", "0"));
    }
}
