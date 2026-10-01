package com.ticketbox.api.module.payment.services;

import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.order.domain.entities.Order;
import com.ticketbox.api.module.order.domain.entities.OrderStatus;
import com.ticketbox.api.module.order.repositories.OrderRepository;
import com.ticketbox.api.module.order.services.OrderInventoryService;
import com.ticketbox.api.module.payment.domain.dtos.CreatePaymentRequest;
import com.ticketbox.api.module.payment.domain.dtos.CreatePaymentResponse;
import com.ticketbox.api.module.payment.domain.dtos.PaymentGatewayRequest;
import com.ticketbox.api.module.payment.domain.dtos.PaymentCallbackResponse;
import com.ticketbox.api.module.payment.domain.dtos.CallbackHandlingResult;
import com.ticketbox.api.module.payment.domain.dtos.GatewayCallback;
import com.ticketbox.api.module.payment.domain.dtos.PaymentResponse;
import com.ticketbox.api.module.payment.domain.entities.Payment;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import com.ticketbox.api.module.payment.gateways.PaymentGatewayStrategy;
import com.ticketbox.api.module.payment.gateways.PaymentStatusQueryGateway;
import com.ticketbox.api.module.payment.gateways.GatewayQueryResult;
import com.ticketbox.api.module.payment.gateways.MomoGateway;
import com.ticketbox.api.module.payment.repositories.PaymentRepository;
import com.ticketbox.api.module.payment.events.PaymentCompletedEvent;
import com.ticketbox.api.module.ticket.consumers.TicketPaymentSucceededConsumer;
import com.ticketbox.api.module.shared.idempotency.IdempotencyService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final Duration RECONCILE_DELAY = Duration.ofSeconds(60);
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:payment:";

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final List<PaymentGatewayStrategy> paymentGateways;
    private final List<PaymentStatusQueryGateway> paymentStatusQueryGateways;
    private final IdempotencyService idempotencyService;
    private final TransactionTemplate transactionTemplate;
    private final OrderInventoryService orderInventoryService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final TicketPaymentSucceededConsumer ticketIssuer;

    @Override
    public PaymentResponse getPayment(User currentUser, UUID paymentId) {
        Payment payment = paymentRepository.findPaymentWithOrderAndUserById(paymentId)
                .orElseThrow(() -> paymentNotFound());

        if (!payment.getOrder().getUser().getId().equals(currentUser.getId())) {
            throw paymentNotFound();
        }
        return mapToPaymentResponse(payment);
    }

    @Override
    public CreatePaymentResponse createPayment(User currentUser, String idempotencyKey, CreatePaymentRequest request,
            String clientIp) {
        String fingerprint = fingerprint(currentUser, request);
        String redisKey = IDEMPOTENCY_KEY_PREFIX + idempotencyKey;

        CreatePaymentResponse result = idempotencyService.execute(
                redisKey,
                fingerprint,
                IDEMPOTENCY_TTL,
                CreatePaymentResponse.class,
                () -> findExistingPayment(currentUser, idempotencyKey, request),
                () -> createNewPayment(currentUser, idempotencyKey, request, clientIp));
        return paymentRepository.findPaymentWithOrderAndUserById(result.paymentId())
                .map(this::mapToResponse)
                .orElse(result);
    }

    @Override
    public Optional<PaymentCallbackResponse> handleCallback(PaymentProvider provider, Map<String, String> parameters) {
        return resolveGateway(provider).responseFor(handleCallbackResult(provider, parameters));
    }

    @Override
    public CallbackHandlingResult handleCallbackResult(PaymentProvider provider, Map<String, String> parameters) {
        PaymentGatewayStrategy gateway = resolveGateway(provider);
        GatewayCallback callback = gateway.verifyCallback(parameters);
        CallbackHandlingResult result;

        if (!callback.signatureValid()) {
            result = CallbackHandlingResult.INVALID_SIGNATURE;
        } else if (callback.paymentId().isEmpty()) {
            result = CallbackHandlingResult.PAYMENT_NOT_FOUND;
        } else {
            result = transactionTemplate
                    .execute(status -> applyCallback(callback.paymentId().orElseThrow(), provider, callback));
            if (result == null) {
                throw new IllegalStateException("Payment callback transaction returned no result");
            }
        }
        return result;
    }

    private CreatePaymentResponse createNewPayment(User currentUser, String idempotencyKey,
            CreatePaymentRequest request, String clientIp) {
        PaymentGatewayStrategy gateway = resolveGateway(request.getProvider());

        // VNPay
        if (request.getProvider() == PaymentProvider.VNPAY) {
            CreatePaymentResponse response = transactionTemplate.execute(status -> {
                Payment payment = initializePayment(currentUser, idempotencyKey, request);
                String checkoutUrl = gateway.createPaymentUrl(gatewayRequest(payment, request, clientIp, gateway));
                return saveCheckoutUrl(payment.getId(), checkoutUrl);
            });
            if (response == null) {
                throw new IllegalStateException("VNPay creation transaction returned no response");
            }
            return response;
        } else {
        // MOMO
            Payment payment = transactionTemplate.execute(status -> initializePayment(currentUser, idempotencyKey, request));
            if (payment == null) {
                throw new IllegalStateException("Payment initialization transaction returned no payment");
            }

            try {
                String checkoutUrl = gateway.createPaymentUrl(gatewayRequest(payment, request, clientIp, gateway));
                return transactionTemplate.execute(status -> saveCheckoutUrl(payment.getId(), checkoutUrl));
            } catch (MomoGateway.CreateOutcomeUnknownException exception) {
                log.warn("MoMo create outcome unknown for payment {}: {}", payment.getId(), exception.getMessage());
                return transactionTemplate.execute(status -> scheduleReconcile(payment.getId()));
            } catch (MomoGateway.CreateRejectedException exception) {
                return transactionTemplate.execute(status -> failCreate(payment.getId(), exception.getMessage()));
            }
        }
    }

    private Payment initializePayment(User currentUser, String idempotencyKey, CreatePaymentRequest request) {
        Order order = orderRepository.findByIdForUpdate(request.getOrderId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"));

        if (!order.getUser().getId().equals(currentUser.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "ORDER_ACCESS_DENIED",
                    "Order does not belong to current user");
        }
        if (order.getStatus() != OrderStatus.HELD) {
            throw new AppException(HttpStatus.CONFLICT, "ORDER_NOT_PAYABLE", "Order is no longer held");
        }
        if (order.getHoldExpiresAt() == null || !LocalDateTime.now().isBefore(order.getHoldExpiresAt())) {
            throw new AppException(HttpStatus.CONFLICT, "ORDER_HOLD_EXPIRED", "Order hold has expired");
        }
        if (paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING)) {
            throw new AppException(HttpStatus.CONFLICT, "PAYMENT_IN_PROGRESS",
                    "Order already has a payment in progress");
        }
        if (paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.CREATING)) {
            throw new AppException(HttpStatus.CONFLICT, "PAYMENT_IN_PROGRESS",
                    "Order already has a payment in progress");
        }
        if (paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCEEDED)) {
            throw new AppException(HttpStatus.CONFLICT, "ORDER_ALREADY_PAID", "Order has already been paid");
        }

        return paymentRepository.saveAndFlush(Payment.builder()
                .order(order)
                .provider(request.getProvider())
                .idempotencyKey(idempotencyKey)
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .status(request.getProvider() == PaymentProvider.MOMO ? PaymentStatus.CREATING : PaymentStatus.PENDING)
                .nextReconcileAt(request.getProvider() == PaymentProvider.MOMO ? nextReconcileAt() : null)
                .build());
    }

    private CreatePaymentResponse saveCheckoutUrl(UUID paymentId, String checkoutUrl) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new IllegalStateException("Initialized payment no longer exists"));
        if (payment.getStatus() == PaymentStatus.CREATING) {
            Order order = orderRepository.findByIdForUpdate(payment.getOrder().getId()).orElseThrow();
            if (!isHoldActive(order)) {
                payment.setNextReconcileAt(nextReconcileAt());
                return mapToResponse(paymentRepository.saveAndFlush(payment));
            }
            payment.setStatus(PaymentStatus.PENDING);
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return mapToResponse(payment);
        }
        payment.setCheckoutUrl(checkoutUrl);
        payment.setNextReconcileAt(nextReconcileAt());
        Payment savedPayment = paymentRepository.saveAndFlush(payment);
        return mapToResponse(savedPayment);
    }

    private CreatePaymentResponse scheduleReconcile(UUID paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow();
        if (payment.getStatus() == PaymentStatus.CREATING || payment.getStatus() == PaymentStatus.PENDING) {
            payment.setNextReconcileAt(nextReconcileAt());
            paymentRepository.saveAndFlush(payment);
        }
        return mapToResponse(payment);
    }

    private CreatePaymentResponse failCreate(UUID paymentId, String reason) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElseThrow();
        if (payment.getStatus() == PaymentStatus.CREATING) {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(reason);
            payment.setNextReconcileAt(null);
            paymentRepository.saveAndFlush(payment);
        }
        return mapToResponse(payment);
    }

    private Optional<CreatePaymentResponse> findExistingPayment(User currentUser, String idempotencyKey,
            CreatePaymentRequest request) {
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .map(payment -> {
                    if (!payment.getOrder().getUser().getId().equals(currentUser.getId())
                            || !payment.getOrder().getId().equals(request.getOrderId())
                            || payment.getProvider() != request.getProvider()) {
                        throw new AppException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REUSED",
                                "Idempotency key was already used for a different request");
                    }
                    return mapToResponse(payment);
                });
    }

    private PaymentGatewayStrategy resolveGateway(PaymentProvider provider) {
        return paymentGateways.stream()
                .filter(gateway -> gateway.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new AppException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "PAYMENT_PROVIDER_UNAVAILABLE", "Payment provider is not available"));
    }

    private CallbackHandlingResult applyCallback(UUID paymentId, PaymentProvider provider, GatewayCallback callback) {
        return applyVerifiedOutcome(paymentId, provider, callback, true);
    }

    private CallbackHandlingResult applyVerifiedOutcome(UUID paymentId, PaymentProvider provider,
            GatewayCallback callback, boolean fromIpn) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElse(null);
        if (payment == null || payment.getProvider() != provider) {
            return CallbackHandlingResult.PAYMENT_NOT_FOUND;
        }
        if (callback.amount().isEmpty() || payment.getAmount().compareTo(callback.amount().orElseThrow()) != 0) {
            return CallbackHandlingResult.AMOUNT_MISMATCH;
        }

        PaymentStatus targetStatus = callback.targetStatus()
                .orElseThrow(() -> new IllegalStateException("Verified callback has no target payment status"));
        if (payment.getProviderTransactionId() != null && callback.providerTransactionId() != null
                && !payment.getProviderTransactionId().equals(callback.providerTransactionId())) {
            log.error("Conflicting provider transaction ID for payment {}", paymentId);
            return CallbackHandlingResult.CONFLICTING_RESULT;
        }
        if (payment.getStatus() != PaymentStatus.PENDING && payment.getStatus() != PaymentStatus.CREATING) {
            if (payment.getStatus() != targetStatus && targetStatus != PaymentStatus.PENDING) {
                log.error("Conflicting {} result for payment {}: stored={}, incoming={}",
                        fromIpn ? "IPN" : "query", paymentId, payment.getStatus(), targetStatus);
                return CallbackHandlingResult.CONFLICTING_RESULT;
            }
            return CallbackHandlingResult.ALREADY_PROCESSED;
        }

        if (fromIpn) {
            payment.setWebhookPayload(writePayload(callback.sanitizedPayload()));
            payment.setWebhookReceivedAt(LocalDateTime.now());
            payment.setWebhookSignatureValid(true);
        } else {
            payment.setProviderPayload(writePayload(callback.sanitizedPayload()));
        }
        if (callback.providerTransactionId() != null && !callback.providerTransactionId().isBlank()) {
            payment.setProviderTransactionId(callback.providerTransactionId());
        }

        if (targetStatus == PaymentStatus.PENDING) {
            payment.setNextReconcileAt(nextReconcileAt());
            paymentRepository.saveAndFlush(payment);
            return CallbackHandlingResult.PROCESSED;
        }

        boolean issueTickets = false;
        if (targetStatus == PaymentStatus.SUCCEEDED) {
            Order order = orderRepository.findByIdForUpdate(payment.getOrder().getId()).orElseThrow();
            issueTickets = payment.getStatus() == PaymentStatus.PENDING && isHoldActive(order);
            payment.setStatus(PaymentStatus.SUCCEEDED);
            LocalDateTime paidAt = LocalDateTime.now();
            payment.setPaidAt(paidAt);
            if (issueTickets) {
                orderInventoryService.settlePaidOrder(order.getId(), paidAt);
            } else {
                payment.setRefundRequired(true);
                payment.setFailureReason("REFUND_REQUIRED: paid after hold expiry or before checkout URL was available");
                log.warn("Payment {} requires manual refund", paymentId);
            }
        } else if (targetStatus == PaymentStatus.FAILED || targetStatus == PaymentStatus.CANCELLED) {
            payment.setStatus(targetStatus);
            payment.setFailureReason(callback.failureReason().orElse("Payment provider rejected payment"));
        }
        payment.setNextReconcileAt(null);

        Payment savedPayment = paymentRepository.saveAndFlush(payment);
        if (issueTickets) {
            PaymentCompletedEvent event = completedEvent(savedPayment);
            ticketIssuer.issueTickets(event);
        } else if (targetStatus == PaymentStatus.FAILED || targetStatus == PaymentStatus.CANCELLED) {
            eventPublisher.publishEvent(new PaymentCompletedEvent(
                    savedPayment.getId(),
                    savedPayment.getOrder().getId(),
                    savedPayment.getOrder().getUser().getId(),
                    savedPayment.getProvider(),
                    savedPayment.getStatus(),
                    savedPayment.getAmount(),
                    savedPayment.getCurrency(),
                    savedPayment.getProviderTransactionId()));
        }
        return CallbackHandlingResult.PROCESSED;
    }

    private PaymentCompletedEvent completedEvent(Payment payment) {
        return new PaymentCompletedEvent(
                payment.getId(), payment.getOrder().getId(), payment.getOrder().getUser().getId(),
                payment.getProvider(), payment.getStatus(), payment.getAmount(), payment.getCurrency(),
                payment.getProviderTransactionId());
    }

    private boolean isHoldActive(Order order) {
        return order.getStatus() == OrderStatus.HELD
                && order.getHoldExpiresAt() != null
                && LocalDateTime.now().isBefore(order.getHoldExpiresAt());
    }

    private LocalDateTime nextReconcileAt() {
        return LocalDateTime.now().plus(RECONCILE_DELAY);
    }

    public void reconcilePayment(UUID paymentId) {
        Payment payment = paymentRepository.findPaymentWithOrderAndUserById(paymentId).orElse(null);
        if (payment == null || payment.getNextReconcileAt() == null
                || payment.getNextReconcileAt().isAfter(LocalDateTime.now())
                || (payment.getStatus() != PaymentStatus.CREATING && payment.getStatus() != PaymentStatus.PENDING)) {
            return;
        }

        try {
            if (payment.getStatus() == PaymentStatus.CREATING && payment.getProvider() == PaymentProvider.MOMO
                    && isHoldActive(payment.getOrder())) {
                PaymentGatewayStrategy gateway = resolveGateway(PaymentProvider.MOMO);
                String url = gateway.createPaymentUrl(PaymentGatewayRequest.builder()
                        .orderId(payment.getOrder().getId())
                        .amount(payment.getAmount())
                        .currency(payment.getCurrency())
                        .txnRef(gateway.transactionReference(paymentId))
                        .orderInfo("Payment order " + gateway.transactionReference(paymentId))
                        .build());
                transactionTemplate.execute(status -> saveCheckoutUrl(paymentId, url));
                return;
            }

            GatewayQueryResult result = resolveQueryGateway(payment.getProvider()).query(payment);
            String expectedReference = resolveGateway(payment.getProvider()).transactionReference(paymentId);
            if (!expectedReference.equals(result.merchantReference())
                    || result.amount() == null || payment.getAmount().compareTo(result.amount()) != 0) {
                throw new IllegalStateException("Gateway query result does not match payment " + paymentId);
            }
            GatewayCallback callback = new GatewayCallback(
                    true, Optional.of(paymentId), Optional.of(result.amount()), result.providerTransactionId(),
                    Optional.of(result.status()), Optional.of("Provider query resultCode=" + result.resultCode()),
                    Map.of("source", "query", "resultCode", result.resultCode()));
            transactionTemplate.execute(status -> applyVerifiedOutcome(paymentId, payment.getProvider(), callback, false));
        } catch (MomoGateway.CreateRejectedException exception) {
            transactionTemplate.execute(status -> failCreate(paymentId, exception.getMessage()));
        } catch (RuntimeException exception) {
            log.warn("Payment reconciliation deferred for {}: {}", paymentId, exception.getMessage());
            transactionTemplate.execute(status -> scheduleReconcile(paymentId));
        }
    }

    private PaymentStatusQueryGateway resolveQueryGateway(PaymentProvider provider) {
        return paymentStatusQueryGateways.stream()
                .filter(gateway -> gateway.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No query gateway for provider " + provider));
    }

    private String writePayload(Map<String, String> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize payment callback payload", exception);
        }
    }

    private PaymentGatewayRequest gatewayRequest(Payment payment, CreatePaymentRequest request, String clientIp,
            PaymentGatewayStrategy gateway) {
        String transactionReference = gateway.transactionReference(payment.getId());
        return PaymentGatewayRequest.builder()
                .orderId(request.getOrderId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .txnRef(transactionReference)
                .orderInfo("Payment order " + transactionReference)
                .bankCode(request.getBankCode())
                .locale(request.getLocale())
                .ipAddress(clientIp)
                .build();
    }

    private CreatePaymentResponse mapToResponse(Payment payment) {
        return CreatePaymentResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrder().getId())
                .provider(payment.getProvider().name())
                .status(payment.getStatus().name())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .checkoutUrl(payment.getCheckoutUrl())
                .holdExpiresAt(payment.getOrder().getHoldExpiresAt())
                .createdAt(payment.getCreatedAt())
                .build();
    }

    private PaymentResponse mapToPaymentResponse(Payment payment) {
        return PaymentResponse.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrder().getId())
                .provider(payment.getProvider().name())
                .status(payment.getStatus().name())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .checkoutUrl(payment.getCheckoutUrl())
                .holdExpiresAt(payment.getOrder().getHoldExpiresAt())
                .paidAt(payment.getPaidAt())
                .failureReason(payment.getFailureReason())
                .refundRequired(payment.isRefundRequired())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .build();
    }

    private AppException paymentNotFound() {
        return new AppException(HttpStatus.NOT_FOUND, "PAYMENT_NOT_FOUND", "Payment not found");
    }

    private String fingerprint(User currentUser, CreatePaymentRequest request) {
        String payload = currentUser.getId() + "|" + request.getOrderId() + "|" + request.getProvider()
                + "|" + nullToEmpty(request.getBankCode()) + "|" + nullToEmpty(request.getLocale());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
