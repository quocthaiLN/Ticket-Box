package com.ticketbox.api.module.payment;

import com.ticketbox.api.module.audit.repositories.AuditLogRepository;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserRole;
import com.ticketbox.api.module.auth.repositories.UserRepository;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.ConcertStatus;
import com.ticketbox.api.module.catalog.repositories.ConcertRepository;
import com.ticketbox.api.module.notification.consumer.NotificationConsumer;
import com.ticketbox.api.module.order.domain.entities.Order;
import com.ticketbox.api.module.order.domain.entities.OrderStatus;
import com.ticketbox.api.module.order.repositories.OrderRepository;
import com.ticketbox.api.module.order.services.OrderInventoryService;
import com.ticketbox.api.module.payment.domain.dtos.CallbackHandlingResult;
import com.ticketbox.api.module.payment.domain.dtos.PaymentOutcome;
import com.ticketbox.api.module.payment.domain.dtos.RefundGatewayRequest;
import com.ticketbox.api.module.payment.domain.dtos.RefundGatewayResult;
import com.ticketbox.api.module.payment.domain.entities.Payment;
import com.ticketbox.api.module.payment.domain.entities.PaymentEvent;
import com.ticketbox.api.module.payment.domain.entities.PaymentEventSource;
import com.ticketbox.api.module.payment.domain.entities.PaymentEventStatus;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import com.ticketbox.api.module.payment.domain.entities.Refund;
import com.ticketbox.api.module.payment.domain.entities.RefundStatus;
import com.ticketbox.api.module.payment.gateways.MomoRefundGateway;
import com.ticketbox.api.module.payment.gateways.VnpayRefundGateway;
import com.ticketbox.api.module.payment.repositories.PaymentEventRepository;
import com.ticketbox.api.module.payment.repositories.PaymentRepository;
import com.ticketbox.api.module.payment.repositories.RefundRepository;
import com.ticketbox.api.module.payment.services.PaymentService;
import com.ticketbox.api.module.ticket.consumers.TicketPaymentSucceededConsumer;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest
class PaymentResilienceIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentEventRepository paymentEventRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ConcertRepository concertRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @MockitoBean
    private OrderInventoryService orderInventoryService;

    @MockitoBean
    private TicketPaymentSucceededConsumer ticketIssuer;

    @MockitoBean
    private NotificationConsumer notificationConsumer;

    @MockitoBean
    private MomoRefundGateway momoRefundGateway;

    @MockitoBean
    private VnpayRefundGateway vnpayRefundGateway;

    private User testUser;
    private Concert testConcert;

    @BeforeEach
    void setUp() {
        refundRepository.deleteAll();
        paymentEventRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        concertRepository.deleteAll();
        userRepository.deleteAll();
        auditLogRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .email("resilience.test." + UUID.randomUUID() + "@example.com")
                .fullName("Resilience Test User")
                .role(UserRole.AUDIENCE)
                .build());

        testConcert = concertRepository.save(Concert.builder()
                .organizer(testUser)
                .title("Resilience Concert")
                .slug("resilience-concert-" + UUID.randomUUID())
                .artistName("Artist Test")
                .venue("Hanoi Opera House")
                .startsAt(LocalDateTime.now().plusDays(3))
                .endsAt(LocalDateTime.now().plusDays(3).plusHours(3))
                .status(ConcertStatus.PUBLISHED)
                .build());

        when(momoRefundGateway.getProvider()).thenReturn(PaymentProvider.MOMO);
        when(vnpayRefundGateway.getProvider()).thenReturn(PaymentProvider.VNPAY);

        when(momoRefundGateway.refund(any(RefundGatewayRequest.class)))
                .thenReturn(RefundGatewayResult.builder()
                        .status(RefundStatus.SUCCESS)
                        .providerRefundId("MOMO_REFUND_" + UUID.randomUUID())
                        .responseCode("0")
                        .message("Refund success")
                        .build());

        when(vnpayRefundGateway.refund(any(RefundGatewayRequest.class)))
                .thenReturn(RefundGatewayResult.builder()
                        .status(RefundStatus.SUCCESS)
                        .providerRefundId("VNP_REFUND_" + UUID.randomUUID())
                        .responseCode("00")
                        .message("Refund success")
                        .build());
    }

    @Test
    @DisplayName("Normal flow: successful IPN settles inventory, issues tickets, and marks payment SUCCESS")
    void testNormalPaymentConvergence_succeedsAndConfirmsOrder() {
        Order order = orderRepository.save(Order.builder()
                .user(testUser)
                .concert(testConcert)
                .totalAmount(new BigDecimal("500000.00"))
                .currency("VND")
                .status(OrderStatus.HELD)
                .holdExpiresAt(LocalDateTime.now().plusMinutes(15))
                .idempotencyKey(UUID.randomUUID().toString())
                .build());

        String merchantTxnRef = "TBX_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Payment payment = paymentRepository.save(Payment.builder()
                .order(order)
                .provider(PaymentProvider.MOMO)
                .merchantTxnRef(merchantTxnRef)
                .idempotencyKey(UUID.randomUUID().toString())
                .amount(new BigDecimal("500000.00"))
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .build());

        String dedupeKey = "MOMO_TXN_001_SUCCESS";
        PaymentOutcome outcome = PaymentOutcome.builder()
                .provider(PaymentProvider.MOMO)
                .source(PaymentEventSource.IPN)
                .dedupeKey(dedupeKey)
                .merchantTxnRef(merchantTxnRef)
                .providerTxnId("MOMO_TXN_001")
                .targetStatus(PaymentStatus.SUCCESS)
                .amount(new BigDecimal("500000.00"))
                .signatureValid(true)
                .rawPayload(Map.of("resultCode", "0", "amount", "500000"))
                .build();

        CallbackHandlingResult result = paymentService.processPaymentResult(outcome);

        assertEquals(CallbackHandlingResult.PROCESSED, result);

        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, updatedPayment.getStatus());
        assertEquals("MOMO_TXN_001", updatedPayment.getProviderTransactionId());
        assertNotNull(updatedPayment.getPaidAt());

        verify(orderInventoryService).settlePaidOrder(eq(order.getId()), any(LocalDateTime.class));
        verify(ticketIssuer).issueTickets(any());

        Optional<PaymentEvent> event = paymentEventRepository.findByProviderAndDedupeKey(PaymentProvider.MOMO, dedupeKey);
        assertTrue(event.isPresent());
        assertEquals(PaymentEventStatus.PROCESSED, event.get().getProcessingStatus());

        assertEquals(0, refundRepository.count());
    }

    @Test
    @DisplayName("Late IPN: hold expired -> payment SUCCESS, order stays EXPIRED, refund requested, NO tickets issued")
    void testLatePaymentConvergence_initiatesRefundAndKeepsOrderExpired() {
        Order expiredOrder = orderRepository.save(Order.builder()
                .user(testUser)
                .concert(testConcert)
                .totalAmount(new BigDecimal("300000.00"))
                .currency("VND")
                .status(OrderStatus.EXPIRED)
                .holdExpiresAt(LocalDateTime.now().minusMinutes(5))
                .expiredAt(LocalDateTime.now().minusMinutes(5))
                .idempotencyKey(UUID.randomUUID().toString())
                .build());

        String merchantTxnRef = "TBX_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Payment payment = paymentRepository.save(Payment.builder()
                .order(expiredOrder)
                .provider(PaymentProvider.VNPAY)
                .merchantTxnRef(merchantTxnRef)
                .idempotencyKey(UUID.randomUUID().toString())
                .amount(new BigDecimal("300000.00"))
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .build());

        String dedupeKey = "VNP_TXN_002_SUCCESS";
        PaymentOutcome outcome = PaymentOutcome.builder()
                .provider(PaymentProvider.VNPAY)
                .source(PaymentEventSource.IPN)
                .dedupeKey(dedupeKey)
                .merchantTxnRef(merchantTxnRef)
                .providerTxnId("VNP_TXN_002")
                .targetStatus(PaymentStatus.SUCCESS)
                .amount(new BigDecimal("300000.00"))
                .signatureValid(true)
                .rawPayload(Map.of("vnp_ResponseCode", "00", "vnp_Amount", "30000000"))
                .build();

        CallbackHandlingResult result = paymentService.processPaymentResult(outcome);

        assertEquals(CallbackHandlingResult.PROCESSED, result);

        Payment updatedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.SUCCESS, updatedPayment.getStatus());
        assertEquals("LATE_PAYMENT_REFUND_INITIATED", updatedPayment.getFailureCode());

        // Absolutely NO tickets issued and NO inventory settled for late payment
        verifyNoInteractions(ticketIssuer);
        verifyNoInteractions(orderInventoryService);

        List<Refund> refunds = refundRepository.findByPaymentIdOrderByRequestedAtAsc(payment.getId());
        assertEquals(1, refunds.size());
        Refund refund = refunds.get(0);
        assertEquals(new BigDecimal("300000.00"), refund.getAmount());
        assertEquals("Late payment after hold expiry", refund.getReason());
        assertEquals(RefundStatus.SUCCESS, refund.getStatus());

        verify(vnpayRefundGateway).refund(any(RefundGatewayRequest.class));
    }

    @Test
    @DisplayName("Idempotency: duplicate IPN does not reprocess or create duplicate refund")
    void testIdempotency_duplicatePaymentEventIsSafelyIgnored() {
        Order order = orderRepository.save(Order.builder()
                .user(testUser)
                .concert(testConcert)
                .totalAmount(new BigDecimal("200000.00"))
                .currency("VND")
                .status(OrderStatus.HELD)
                .holdExpiresAt(LocalDateTime.now().plusMinutes(15))
                .idempotencyKey(UUID.randomUUID().toString())
                .build());

        String merchantTxnRef = "TBX_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Payment payment = paymentRepository.save(Payment.builder()
                .order(order)
                .provider(PaymentProvider.MOMO)
                .merchantTxnRef(merchantTxnRef)
                .idempotencyKey(UUID.randomUUID().toString())
                .amount(new BigDecimal("200000.00"))
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .build());

        String dedupeKey = "MOMO_TXN_DUP_SUCCESS";
        PaymentOutcome outcome = PaymentOutcome.builder()
                .provider(PaymentProvider.MOMO)
                .source(PaymentEventSource.IPN)
                .dedupeKey(dedupeKey)
                .merchantTxnRef(merchantTxnRef)
                .providerTxnId("MOMO_TXN_DUP")
                .targetStatus(PaymentStatus.SUCCESS)
                .amount(new BigDecimal("200000.00"))
                .signatureValid(true)
                .rawPayload(Map.of("resultCode", "0", "amount", "200000"))
                .build();

        CallbackHandlingResult result1 = paymentService.processPaymentResult(outcome);
        assertEquals(CallbackHandlingResult.PROCESSED, result1);

        CallbackHandlingResult result2 = paymentService.processPaymentResult(outcome);
        assertEquals(CallbackHandlingResult.ALREADY_PROCESSED, result2);

        assertEquals(1, paymentEventRepository.findByPaymentIdOrderByReceivedAtAsc(payment.getId()).size());
    }

    @Test
    @DisplayName("Invalid signature: logs ignored event without modifying payment")
    void testInvalidSignature_logsIgnoredEvent_doesNotModifyPayment() {
        Order order = orderRepository.save(Order.builder()
                .user(testUser)
                .concert(testConcert)
                .totalAmount(new BigDecimal("150000.00"))
                .currency("VND")
                .status(OrderStatus.HELD)
                .holdExpiresAt(LocalDateTime.now().plusMinutes(15))
                .idempotencyKey(UUID.randomUUID().toString())
                .build());

        String merchantTxnRef = "TBX_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Payment payment = paymentRepository.save(Payment.builder()
                .order(order)
                .provider(PaymentProvider.VNPAY)
                .merchantTxnRef(merchantTxnRef)
                .idempotencyKey(UUID.randomUUID().toString())
                .amount(new BigDecimal("150000.00"))
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .build());

        PaymentOutcome outcome = PaymentOutcome.builder()
                .provider(PaymentProvider.VNPAY)
                .source(PaymentEventSource.IPN)
                .dedupeKey("VNP_TXN_INVALID_SIG")
                .merchantTxnRef(merchantTxnRef)
                .providerTxnId("VNP_TXN_INVALID")
                .targetStatus(PaymentStatus.SUCCESS)
                .amount(new BigDecimal("150000.00"))
                .signatureValid(false)
                .rawPayload(Map.of("vnp_ResponseCode", "00"))
                .build();

        CallbackHandlingResult result = paymentService.processPaymentResult(outcome);

        assertEquals(CallbackHandlingResult.INVALID_SIGNATURE, result);

        Payment unchangedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.PENDING, unchangedPayment.getStatus());

        List<PaymentEvent> events = paymentEventRepository.findByPaymentIdOrderByReceivedAtAsc(payment.getId());
        assertEquals(1, events.size());
        assertFalse(events.get(0).getSignatureValid());
        assertEquals(PaymentEventStatus.IGNORED, events.get(0).getProcessingStatus());
        assertTrue(events.get(0).getDedupeKey().startsWith("INVALID_"));
    }

    @Test
    @DisplayName("Amount mismatch: rejects payment and leaves order HELD")
    void testAmountMismatch_rejectsPayment_doesNotConfirmOrder() {
        Order order = orderRepository.save(Order.builder()
                .user(testUser)
                .concert(testConcert)
                .totalAmount(new BigDecimal("400000.00"))
                .currency("VND")
                .status(OrderStatus.HELD)
                .holdExpiresAt(LocalDateTime.now().plusMinutes(15))
                .idempotencyKey(UUID.randomUUID().toString())
                .build());

        String merchantTxnRef = "TBX_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Payment payment = paymentRepository.save(Payment.builder()
                .order(order)
                .provider(PaymentProvider.MOMO)
                .merchantTxnRef(merchantTxnRef)
                .idempotencyKey(UUID.randomUUID().toString())
                .amount(new BigDecimal("400000.00"))
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .build());

        PaymentOutcome outcome = PaymentOutcome.builder()
                .provider(PaymentProvider.MOMO)
                .source(PaymentEventSource.IPN)
                .dedupeKey("MOMO_MISMATCH_" + UUID.randomUUID())
                .merchantTxnRef(merchantTxnRef)
                .providerTxnId("MOMO_TXN_MISMATCH")
                .targetStatus(PaymentStatus.SUCCESS)
                .amount(new BigDecimal("100000.00")) // Mismatched amount
                .signatureValid(true)
                .rawPayload(Map.of("resultCode", "0", "amount", "100000"))
                .build();

        CallbackHandlingResult result = paymentService.processPaymentResult(outcome);

        assertEquals(CallbackHandlingResult.AMOUNT_MISMATCH, result);

        Payment unchangedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.PENDING, unchangedPayment.getStatus());

        verifyNoInteractions(ticketIssuer);
        verifyNoInteractions(orderInventoryService);
    }

    @Test
    @DisplayName("Failed payment outcome: marks payment FAILED and keeps order HELD")
    void testFailedPaymentOutcome_marksPaymentFailed_keepsOrderHeld() {
        Order order = orderRepository.save(Order.builder()
                .user(testUser)
                .concert(testConcert)
                .totalAmount(new BigDecimal("250000.00"))
                .currency("VND")
                .status(OrderStatus.HELD)
                .holdExpiresAt(LocalDateTime.now().plusMinutes(15))
                .idempotencyKey(UUID.randomUUID().toString())
                .build());

        String merchantTxnRef = "TBX_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Payment payment = paymentRepository.save(Payment.builder()
                .order(order)
                .provider(PaymentProvider.MOMO)
                .merchantTxnRef(merchantTxnRef)
                .idempotencyKey(UUID.randomUUID().toString())
                .amount(new BigDecimal("250000.00"))
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .build());

        PaymentOutcome outcome = PaymentOutcome.builder()
                .provider(PaymentProvider.MOMO)
                .source(PaymentEventSource.IPN)
                .dedupeKey("MOMO_FAILED_" + UUID.randomUUID())
                .merchantTxnRef(merchantTxnRef)
                .providerTxnId("MOMO_TXN_FAILED")
                .targetStatus(PaymentStatus.FAILED)
                .amount(new BigDecimal("250000.00"))
                .signatureValid(true)
                .failureCode("1004")
                .failureMessage("Payment declined by user")
                .rawPayload(Map.of("resultCode", "1004"))
                .build();

        CallbackHandlingResult result = paymentService.processPaymentResult(outcome);

        assertEquals(CallbackHandlingResult.PROCESSED, result);

        Payment failedPayment = paymentRepository.findById(payment.getId()).orElseThrow();
        assertEquals(PaymentStatus.FAILED, failedPayment.getStatus());
        assertEquals("1004", failedPayment.getFailureCode());

        verifyNoInteractions(ticketIssuer);
    }
}
