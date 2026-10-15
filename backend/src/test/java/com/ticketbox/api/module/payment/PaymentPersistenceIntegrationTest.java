package com.ticketbox.api.module.payment;

import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserRole;
import com.ticketbox.api.module.auth.repositories.UserRepository;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.ConcertStatus;
import com.ticketbox.api.module.catalog.repositories.ConcertRepository;
import com.ticketbox.api.module.order.domain.entities.Order;
import com.ticketbox.api.module.order.domain.entities.OrderStatus;
import com.ticketbox.api.module.order.repositories.OrderRepository;
import com.ticketbox.api.module.payment.domain.entities.Payment;
import com.ticketbox.api.module.payment.domain.entities.PaymentEvent;
import com.ticketbox.api.module.payment.domain.entities.PaymentEventSource;
import com.ticketbox.api.module.payment.domain.entities.PaymentEventStatus;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import com.ticketbox.api.module.payment.domain.entities.Refund;
import com.ticketbox.api.module.payment.domain.entities.RefundStatus;
import com.ticketbox.api.module.payment.repositories.PaymentEventRepository;
import com.ticketbox.api.module.payment.repositories.PaymentRepository;
import com.ticketbox.api.module.payment.repositories.RefundRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PaymentPersistenceIntegrationTest {

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
    private PlatformTransactionManager transactionManager;

    private User testUser;
    private Concert testConcert;
    private Order testOrder;

    @BeforeEach
    void setUp() {
        refundRepository.deleteAll();
        paymentEventRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        concertRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .email("test.payment.persistence." + UUID.randomUUID() + "@example.com")
                .fullName("Payment Test User")
                .role(UserRole.AUDIENCE)
                .build());

        testConcert = concertRepository.save(Concert.builder()
                .organizer(testUser)
                .title("Persistence Test Concert")
                .slug("persistence-test-concert-" + UUID.randomUUID())
                .artistName("Test Artist")
                .venue("Hanoi Arena")
                .startsAt(LocalDateTime.now().plusDays(2))
                .endsAt(LocalDateTime.now().plusDays(2).plusHours(4))
                .status(ConcertStatus.PUBLISHED)
                .build());

        testOrder = orderRepository.save(Order.builder()
                .user(testUser)
                .concert(testConcert)
                .totalAmount(new BigDecimal("500000.00"))
                .currency("VND")
                .status(OrderStatus.HELD)
                .holdExpiresAt(LocalDateTime.now().plusMinutes(10))
                .idempotencyKey(UUID.randomUUID().toString())
                .build());
    }

    @Test
    @DisplayName("PaymentRepository: persists payment and queries by merchant_txn_ref")
    void testPaymentMerchantTxnRefPersistence() {
        String merchantRef = "TXN_REF_" + UUID.randomUUID().toString().replace("-", "");
        Payment payment = paymentRepository.saveAndFlush(Payment.builder()
                .order(testOrder)
                .provider(PaymentProvider.VNPAY)
                .merchantTxnRef(merchantRef)
                .idempotencyKey(UUID.randomUUID().toString())
                .amount(new BigDecimal("500000.00"))
                .currency("VND")
                .status(PaymentStatus.PENDING)
                .checkoutUrl("https://sandbox.vnpayment.vn/test")
                .build());

        assertNotNull(payment.getId());
        assertEquals(0L, payment.getVersion());

        Optional<Payment> found = paymentRepository.findByMerchantTxnRef(merchantRef);
        assertTrue(found.isPresent());
        assertEquals(payment.getId(), found.get().getId());
        assertEquals(merchantRef, found.get().getMerchantTxnRef());

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            Optional<Payment> foundForUpdate = paymentRepository.findByMerchantTxnRefForUpdate(merchantRef);
            assertTrue(foundForUpdate.isPresent());
            assertEquals(payment.getId(), foundForUpdate.get().getId());
            return null;
        });
    }

    @Test
    @DisplayName("PaymentEventRepository: persists payment events and queries by provider and dedupe_key")
    void testPaymentEventPersistence() {
        Payment payment = paymentRepository.saveAndFlush(Payment.builder()
                .order(testOrder)
                .provider(PaymentProvider.MOMO)
                .idempotencyKey(UUID.randomUUID().toString())
                .amount(new BigDecimal("500000.00"))
                .currency("VND")
                .status(PaymentStatus.CREATING)
                .build());

        String dedupeKey = "DEDUPE_MOMO_" + UUID.randomUUID();
        PaymentEvent event = paymentEventRepository.saveAndFlush(PaymentEvent.builder()
                .payment(payment)
                .provider(PaymentProvider.MOMO)
                .eventSource(PaymentEventSource.IPN)
                .dedupeKey(dedupeKey)
                .eventType("PAYMENT_SUCCESS")
                .rawPayload("{\"resultCode\":0,\"amount\":500000}")
                .signatureValid(true)
                .processingStatus(PaymentEventStatus.RECEIVED)
                .build());

        assertNotNull(event.getId());

        assertTrue(paymentEventRepository.existsByProviderAndDedupeKey(PaymentProvider.MOMO, dedupeKey));
        Optional<PaymentEvent> foundEvent = paymentEventRepository.findByProviderAndDedupeKey(PaymentProvider.MOMO, dedupeKey);
        assertTrue(foundEvent.isPresent());
        assertEquals("PAYMENT_SUCCESS", foundEvent.get().getEventType());
        assertEquals(payment.getId(), foundEvent.get().getPayment().getId());

        List<PaymentEvent> eventsForPayment = paymentEventRepository.findByPaymentIdOrderByReceivedAtAsc(payment.getId());
        assertEquals(1, eventsForPayment.size());
    }

    @Test
    @DisplayName("RefundRepository: persists refunds and queries by idempotencyKey and status")
    void testRefundPersistence() {
        Payment payment = paymentRepository.saveAndFlush(Payment.builder()
                .order(testOrder)
                .provider(PaymentProvider.MOMO)
                .idempotencyKey(UUID.randomUUID().toString())
                .amount(new BigDecimal("500000.00"))
                .currency("VND")
                .status(PaymentStatus.SUCCEEDED)
                .paidAt(LocalDateTime.now())
                .build());

        String refundKey = "LATE_REFUND_" + UUID.randomUUID();
        Refund refund = refundRepository.saveAndFlush(Refund.builder()
                .payment(payment)
                .amount(new BigDecimal("500000.00"))
                .currency("VND")
                .idempotencyKey(refundKey)
                .status(RefundStatus.REQUESTED)
                .reason("Late IPN refund")
                .requestedBy(testUser.getId())
                .build());

        assertNotNull(refund.getId());

        assertTrue(refundRepository.existsByIdempotencyKey(refundKey));
        Optional<Refund> foundRefund = refundRepository.findByIdempotencyKey(refundKey);
        assertTrue(foundRefund.isPresent());
        assertEquals(RefundStatus.REQUESTED, foundRefund.get().getStatus());
        assertEquals(payment.getId(), foundRefund.get().getPayment().getId());

        List<Refund> requestedRefunds = refundRepository.findByStatusIn(List.of(RefundStatus.REQUESTED, RefundStatus.PROCESSING));
        assertEquals(1, requestedRefunds.size());

        List<Refund> paymentRefunds = refundRepository.findByPaymentIdOrderByRequestedAtAsc(payment.getId());
        assertEquals(1, paymentRefunds.size());
    }
}
