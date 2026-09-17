package com.ticketbox.api.module.payment.services;

import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.order.domain.entities.Order;
import com.ticketbox.api.module.order.domain.entities.OrderStatus;
import com.ticketbox.api.module.order.repositories.OrderRepository;
import com.ticketbox.api.module.payment.domain.dtos.CreatePaymentRequest;
import com.ticketbox.api.module.payment.domain.dtos.CreatePaymentResponse;
import com.ticketbox.api.module.payment.domain.dtos.PaymentGatewayRequest;
import com.ticketbox.api.module.payment.domain.dtos.PaymentCallbackResponse;
import com.ticketbox.api.module.payment.domain.dtos.PaymentVerificationResult;
import com.ticketbox.api.module.payment.domain.dtos.PaymentResponse;
import com.ticketbox.api.module.payment.domain.dtos.MomoIpnRequest;
import com.ticketbox.api.module.payment.domain.entities.Payment;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import com.ticketbox.api.module.payment.gateways.PaymentGatewayStrategy;
import com.ticketbox.api.module.payment.repositories.PaymentRepository;
import com.ticketbox.api.module.payment.events.PaymentCompletedEvent;
import com.ticketbox.api.module.shared.idempotency.IdempotencyService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
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

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:payment:";

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final List<PaymentGatewayStrategy> paymentGateways;
    private final IdempotencyService idempotencyService;
    private final TransactionTemplate transactionTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

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

        return idempotencyService.execute(
                redisKey,
                fingerprint,
                IDEMPOTENCY_TTL,
                CreatePaymentResponse.class,
                () -> findExistingPayment(currentUser, idempotencyKey, request),
                () -> createNewPayment(currentUser, idempotencyKey, request, clientIp));
    }

    @Override
    public PaymentCallbackResponse handleVnpayIpn(Map<String, String> parameters) {
        PaymentGatewayStrategy gateway = resolveGateway(PaymentProvider.VNPAY);
        PaymentVerificationResult verification = gateway.verifyCallback(parameters);
        if (!verification.signatureValid()) {
            return PaymentCallbackResponse.of("97", "Invalid checksum");
        }

        UUID paymentId = paymentIdFromTransactionReference(verification.transactionReference());
        if (paymentId == null) {
            return PaymentCallbackResponse.of("01", "Order not found");
        }

        return transactionTemplate.execute(status -> completeVnpayPayment(paymentId, verification, parameters));
    }

    @Override
    public void handleMomoIpn(MomoIpnRequest request) {
        PaymentGatewayStrategy gateway = resolveGateway(PaymentProvider.MOMO);
        Map<String, String> parameters = request.toParameters();
        PaymentVerificationResult verification = gateway.verifyCallback(parameters);
        if (!verification.signatureValid()) {
            return;
        }

        UUID paymentId = paymentIdFromMomoOrderId(verification.transactionReference());
        if (paymentId == null) {
            return;
        }

        transactionTemplate.executeWithoutResult(status -> completeMomoPayment(paymentId, verification, parameters));
    }

    private CreatePaymentResponse createNewPayment(User currentUser, String idempotencyKey,
            CreatePaymentRequest request, String clientIp) {
        PaymentGatewayStrategy gateway = resolveGateway(request.getProvider());
        Payment payment = transactionTemplate.execute(status -> initializePayment(currentUser, idempotencyKey, request));
        if (payment == null) {
            throw new IllegalStateException("Payment initialization transaction returned no payment");
        }

        String checkoutUrl = gateway.createPaymentUrl(gatewayRequest(payment, request, clientIp));

        return transactionTemplate.execute(status -> saveCheckoutUrl(payment.getId(), checkoutUrl));
    }

    private Payment initializePayment(User currentUser, String idempotencyKey, CreatePaymentRequest request) {
        Order order = orderRepository.findByIdForUpdate(request.getOrderId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found"));

        if (!order.getUser().getId().equals(currentUser.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "ORDER_ACCESS_DENIED", "Order does not belong to current user");
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
        if (paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCEEDED)) {
            throw new AppException(HttpStatus.CONFLICT, "ORDER_ALREADY_PAID", "Order has already been paid");
        }

        return paymentRepository.saveAndFlush(Payment.builder()
                .order(order)
                .provider(request.getProvider())
                .idempotencyKey(idempotencyKey)
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .status(PaymentStatus.PENDING)
                .build());
    }

    private CreatePaymentResponse saveCheckoutUrl(UUID paymentId, String checkoutUrl) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new IllegalStateException("Initialized payment no longer exists"));
        payment.setCheckoutUrl(checkoutUrl);
        Payment savedPayment = paymentRepository.saveAndFlush(payment);
        return mapToResponse(savedPayment);
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

    private PaymentCallbackResponse completeVnpayPayment(UUID paymentId, PaymentVerificationResult verification,
            Map<String, String> parameters) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElse(null);
        if (payment == null || payment.getProvider() != PaymentProvider.VNPAY) {
            return PaymentCallbackResponse.of("01", "Order not found");
        }
        if (payment.getStatus() != PaymentStatus.PENDING) {
            return PaymentCallbackResponse.of("02", "Order already confirmed");
        }
        if (!amountMatches(payment, verification.amount())) {
            return PaymentCallbackResponse.of("04", "Invalid amount");
        }

        payment.setWebhookPayload(writePayload(parameters));
        payment.setWebhookReceivedAt(LocalDateTime.now());
        payment.setWebhookSignatureValid(true);
        payment.setProviderTransactionId(verification.providerTransactionId());

        if (verification.successful()) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setPaidAt(LocalDateTime.now());
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("VNPAY response=" + verification.responseCode()
                    + ", transactionStatus=" + verification.transactionStatus());
        }
        Payment savedPayment = paymentRepository.saveAndFlush(payment);
        eventPublisher.publishEvent(new PaymentCompletedEvent(
                savedPayment.getId(),
                savedPayment.getOrder().getId(),
                savedPayment.getOrder().getUser().getId(),
                savedPayment.getProvider(),
                savedPayment.getStatus(),
                savedPayment.getAmount(),
                savedPayment.getCurrency(),
                savedPayment.getProviderTransactionId()));
        return PaymentCallbackResponse.of("00", "Confirm Success");
    }

    private void completeMomoPayment(UUID paymentId, PaymentVerificationResult verification,
            Map<String, String> parameters) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElse(null);
        if (payment == null || payment.getProvider() != PaymentProvider.MOMO || payment.getStatus() != PaymentStatus.PENDING) {
            return;
        }
        if (!momoAmountMatches(payment, verification.amount())) {
            return;
        }

        payment.setWebhookPayload(writePayload(withoutSignature(parameters)));
        payment.setWebhookReceivedAt(LocalDateTime.now());
        payment.setWebhookSignatureValid(true);
        payment.setProviderTransactionId(verification.providerTransactionId());

        if ("0".equals(verification.responseCode())) {
            payment.setStatus(PaymentStatus.SUCCEEDED);
            payment.setPaidAt(LocalDateTime.now());
        } else if (isMomoPending(verification.responseCode())) {
            paymentRepository.saveAndFlush(payment);
            return;
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("MOMO resultCode=" + verification.responseCode());
        }

        Payment savedPayment = paymentRepository.saveAndFlush(payment);
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

    private boolean amountMatches(Payment payment, String providerAmount) {
        try {
            return payment.getAmount().movePointRight(2).toBigIntegerExact()
                    .equals(new java.math.BigInteger(providerAmount));
        } catch (ArithmeticException | NumberFormatException exception) {
            return false;
        }
    }

    private boolean momoAmountMatches(Payment payment, String providerAmount) {
        try {
            return payment.getAmount().toBigIntegerExact().equals(new java.math.BigInteger(providerAmount));
        } catch (ArithmeticException | NumberFormatException exception) {
            return false;
        }
    }

    private boolean isMomoPending(String resultCode) {
        return "1000".equals(resultCode) || "7000".equals(resultCode) || "7002".equals(resultCode);
    }

    private UUID paymentIdFromTransactionReference(String transactionReference) {
        if (transactionReference == null || !transactionReference.matches("[0-9a-fA-F]{32}")) {
            return null;
        }
        String hyphenated = transactionReference.replaceFirst(
                "(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5");
        try {
            return UUID.fromString(hyphenated);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private UUID paymentIdFromMomoOrderId(String orderId) {
        try {
            return UUID.fromString(orderId);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return null;
        }
    }

    private String writePayload(Map<String, String> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize VNPAY callback payload", exception);
        }
    }

    private Map<String, String> withoutSignature(Map<String, String> parameters) {
        Map<String, String> sanitized = new LinkedHashMap<>(parameters);
        sanitized.remove("signature");
        return sanitized;
    }

    private PaymentGatewayRequest gatewayRequest(Payment payment, CreatePaymentRequest request, String clientIp) {
        String transactionReference = request.getProvider() == PaymentProvider.MOMO
                ? payment.getId().toString()
                : payment.getId().toString().replace("-", "");
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
