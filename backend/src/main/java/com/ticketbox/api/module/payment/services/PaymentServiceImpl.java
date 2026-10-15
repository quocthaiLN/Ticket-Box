package com.ticketbox.api.module.payment.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.module.audit.domain.entities.AuditAction;
import com.ticketbox.api.module.audit.services.AuditLogService;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.order.domain.entities.Order;
import com.ticketbox.api.module.order.domain.entities.OrderStatus;
import com.ticketbox.api.module.order.repositories.OrderRepository;
import com.ticketbox.api.module.order.services.OrderInventoryService;
import com.ticketbox.api.module.payment.domain.dtos.CallbackHandlingResult;
import com.ticketbox.api.module.payment.domain.dtos.CreatePaymentRequest;
import com.ticketbox.api.module.payment.domain.dtos.CreatePaymentResponse;
import com.ticketbox.api.module.payment.domain.dtos.GatewayCallback;
import com.ticketbox.api.module.payment.domain.dtos.GatewayQueryResult;
import com.ticketbox.api.module.payment.domain.dtos.PaymentCallbackResponse;
import com.ticketbox.api.module.payment.domain.dtos.PaymentGatewayRequest;
import com.ticketbox.api.module.payment.domain.dtos.PaymentOutcome;
import com.ticketbox.api.module.payment.domain.dtos.PaymentResponse;
import com.ticketbox.api.module.payment.domain.entities.Payment;
import com.ticketbox.api.module.payment.domain.entities.PaymentEvent;
import com.ticketbox.api.module.payment.domain.entities.PaymentEventSource;
import com.ticketbox.api.module.payment.domain.entities.PaymentEventStatus;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import com.ticketbox.api.module.payment.domain.entities.Refund;
import com.ticketbox.api.module.payment.domain.mappers.PaymentMapper;
import com.ticketbox.api.module.payment.events.PaymentCompletedEvent;
import com.ticketbox.api.module.payment.gateways.MomoGateway;
import com.ticketbox.api.module.payment.gateways.PaymentGatewayStrategy;
import com.ticketbox.api.module.payment.gateways.PaymentStatusQueryGateway;
import com.ticketbox.api.module.payment.repositories.PaymentEventRepository;
import com.ticketbox.api.module.payment.repositories.PaymentRepository;
import com.ticketbox.api.module.shared.idempotency.IdempotencyService;
import com.ticketbox.api.module.ticket.consumers.TicketPaymentSucceededConsumer;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final Duration RECONCILE_DELAY = Duration.ofSeconds(60);
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:payment:";
    private static final UUID SYSTEM_ACTOR_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final List<PaymentGatewayStrategy> paymentGateways;
    private final List<PaymentStatusQueryGateway> paymentStatusQueryGateways;
    private final IdempotencyService idempotencyService;
    private final TransactionTemplate transactionTemplate;
    private final OrderInventoryService orderInventoryService;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;
    private final TicketPaymentSucceededConsumer ticketIssuer;
    private final PaymentMapper paymentMapper;
    private final RefundService refundService;
    private final AuditLogService auditLogService;

    public PaymentServiceImpl(
            OrderRepository orderRepository,
            PaymentRepository paymentRepository,
            PaymentEventRepository paymentEventRepository,
            List<PaymentGatewayStrategy> paymentGateways,
            List<PaymentStatusQueryGateway> paymentStatusQueryGateways,
            IdempotencyService idempotencyService,
            TransactionTemplate transactionTemplate,
            OrderInventoryService orderInventoryService,
            ApplicationEventPublisher eventPublisher,
            ObjectMapper objectMapper,
            TicketPaymentSucceededConsumer ticketIssuer,
            PaymentMapper paymentMapper,
            RefundService refundService,
            AuditLogService auditLogService) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentEventRepository = paymentEventRepository;
        this.paymentGateways = paymentGateways;
        this.paymentStatusQueryGateways = paymentStatusQueryGateways;
        this.idempotencyService = idempotencyService;
        this.transactionTemplate = transactionTemplate;
        this.orderInventoryService = orderInventoryService;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
        this.ticketIssuer = ticketIssuer;
        this.paymentMapper = paymentMapper;
        this.refundService = refundService;
        this.auditLogService = auditLogService;
    }

    @Override
    public PaymentResponse getPayment(User currentUser, UUID paymentId) {
        Payment payment = paymentRepository.findPaymentWithOrderAndUserById(paymentId)
                .orElseThrow(() -> paymentNotFound());

        if (!payment.getOrder().getUser().getId().equals(currentUser.getId())) {
            throw paymentNotFound();
        }
        return paymentMapper.toPaymentResponse(payment);
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

        String merchantTxnRef = null;
        if (provider == PaymentProvider.VNPAY) {
            merchantTxnRef = parameters.get("vnp_TxnRef");
        } else if (provider == PaymentProvider.MOMO) {
            merchantTxnRef = parameters.get("orderId");
        }
        if (merchantTxnRef == null && callback.paymentId().isPresent()) {
            merchantTxnRef = callback.paymentId().get().toString();
        }

        String dedupeKey = callback.providerTransactionId() != null && !callback.providerTransactionId().isBlank()
                ? callback.providerTransactionId() + "_" + (callback.targetStatus().isPresent() ? callback.targetStatus().get().name() : "EVENT")
                : (merchantTxnRef != null ? merchantTxnRef + "_CB" : UUID.randomUUID().toString());

        PaymentOutcome outcome = PaymentOutcome.builder()
                .provider(provider)
                .source(PaymentEventSource.IPN)
                .dedupeKey(dedupeKey)
                .merchantTxnRef(merchantTxnRef)
                .providerTxnId(callback.providerTransactionId())
                .targetStatus(callback.targetStatus().orElse(null))
                .amount(callback.amount().orElse(null))
                .signatureValid(callback.signatureValid())
                .failureMessage(callback.failureReason().orElse(null))
                .rawPayload(callback.sanitizedPayload() != null ? callback.sanitizedPayload() : parameters)
                .build();

        return processPaymentResult(outcome);
    }

    @Override
    public CallbackHandlingResult processPaymentResult(PaymentOutcome outcome) {
        if (!outcome.signatureValid()) {
            PaymentEvent event = PaymentEvent.builder()
                    .payment(findPaymentOrNull(outcome.merchantTxnRef()))
                    .provider(outcome.provider())
                    .eventSource(outcome.source() != null ? outcome.source() : PaymentEventSource.IPN)
                    .dedupeKey("INVALID_" + UUID.randomUUID())
                    .eventType(outcome.targetStatus() != null ? outcome.targetStatus().name() : "UNKNOWN")
                    .rawPayload(writePayload(outcome.rawPayload()))
                    .signatureValid(false)
                    .processingStatus(PaymentEventStatus.IGNORED)
                    .errorMessage("Invalid signature")
                    .receivedAt(LocalDateTime.now())
                    .build();
            paymentEventRepository.saveAndFlush(event);

            auditLogService.logInvestigativeFailure(
                    AuditAction.PAYMENT_WEBHOOK_REJECTED,
                    "PAYMENT",
                    outcome.merchantTxnRef() != null ? outcome.merchantTxnRef() : "UNKNOWN",
                    Map.of(
                            "reason", "INVALID_SIGNATURE",
                            "provider", outcome.provider() != null ? outcome.provider().name() : "UNKNOWN",
                            "payment_event_id", safeEventId(event)));

            return CallbackHandlingResult.INVALID_SIGNATURE;
        }

        record ConvergenceTxResult(CallbackHandlingResult result, UUID refundIdToExecute) {}

        ConvergenceTxResult txResult = transactionTemplate.execute(status -> {
            Optional<PaymentEvent> existingEvent = paymentEventRepository.findByProviderAndDedupeKey(
                    outcome.provider(), outcome.dedupeKey());
            if (existingEvent.isPresent() && existingEvent.get().getProcessingStatus() == PaymentEventStatus.PROCESSED) {
                return new ConvergenceTxResult(CallbackHandlingResult.ALREADY_PROCESSED, null);
            }

            Payment payment = findPaymentForUpdate(outcome.merchantTxnRef()).orElse(null);
            if (payment == null || (outcome.provider() != null && payment.getProvider() != outcome.provider())) {
                PaymentEvent event = PaymentEvent.builder()
                        .provider(outcome.provider())
                        .eventSource(outcome.source() != null ? outcome.source() : PaymentEventSource.IPN)
                        .dedupeKey(outcome.dedupeKey())
                        .eventType(outcome.targetStatus() != null ? outcome.targetStatus().name() : "UNKNOWN")
                        .rawPayload(writePayload(outcome.rawPayload()))
                        .signatureValid(true)
                        .processingStatus(PaymentEventStatus.FAILED)
                        .errorMessage("Payment not found for reference: " + outcome.merchantTxnRef())
                        .receivedAt(LocalDateTime.now())
                        .build();
                paymentEventRepository.saveAndFlush(event);

                auditLogService.logInvestigativeFailure(
                        AuditAction.PAYMENT_WEBHOOK_REJECTED,
                        "PAYMENT",
                        outcome.merchantTxnRef() != null ? outcome.merchantTxnRef() : "UNKNOWN",
                        Map.of(
                                "reason", "PAYMENT_NOT_FOUND",
                                "provider", outcome.provider() != null ? outcome.provider().name() : "UNKNOWN",
                                "payment_event_id", safeEventId(event)));

                return new ConvergenceTxResult(CallbackHandlingResult.PAYMENT_NOT_FOUND, null);
            }

            if (outcome.amount() == null || payment.getAmount().compareTo(outcome.amount()) != 0) {
                PaymentEvent event = PaymentEvent.builder()
                        .payment(payment)
                        .provider(outcome.provider())
                        .eventSource(outcome.source() != null ? outcome.source() : PaymentEventSource.IPN)
                        .dedupeKey(outcome.dedupeKey())
                        .eventType(outcome.targetStatus() != null ? outcome.targetStatus().name() : "UNKNOWN")
                        .rawPayload(writePayload(outcome.rawPayload()))
                        .signatureValid(true)
                        .processingStatus(PaymentEventStatus.FAILED)
                        .errorMessage("Amount mismatch: expected " + payment.getAmount() + " but got " + outcome.amount())
                        .receivedAt(LocalDateTime.now())
                        .build();
                paymentEventRepository.saveAndFlush(event);

                auditLogService.logInvestigativeFailure(
                        AuditAction.PAYMENT_WEBHOOK_REJECTED,
                        "PAYMENT",
                        payment.getId().toString(),
                        Map.of(
                                "reason", "AMOUNT_MISMATCH",
                                "provider", outcome.provider().name(),
                                "payment_event_id", safeEventId(event)));

                return new ConvergenceTxResult(CallbackHandlingResult.AMOUNT_MISMATCH, null);
            }

            if (payment.getProviderTransactionId() != null && outcome.providerTxnId() != null
                    && !payment.getProviderTransactionId().equals(outcome.providerTxnId())) {
                log.error("Conflicting provider transaction ID for payment {}", payment.getId());
                return new ConvergenceTxResult(CallbackHandlingResult.CONFLICTING_RESULT, null);
            }

            PaymentStatus currentStatus = payment.getStatus();
            PaymentStatus targetStatus = outcome.targetStatus() != null ? outcome.targetStatus() : PaymentStatus.PENDING;
            if (isTerminalStatus(currentStatus)) {
                if (isSameTerminalStatus(currentStatus, targetStatus)) {
                    return new ConvergenceTxResult(CallbackHandlingResult.ALREADY_PROCESSED, null);
                }
                PaymentEvent event = PaymentEvent.builder()
                        .payment(payment)
                        .provider(outcome.provider())
                        .eventSource(outcome.source() != null ? outcome.source() : PaymentEventSource.IPN)
                        .dedupeKey(outcome.dedupeKey())
                        .eventType(targetStatus.name())
                        .rawPayload(writePayload(outcome.rawPayload()))
                        .signatureValid(true)
                        .processingStatus(PaymentEventStatus.FAILED)
                        .errorMessage("Payment result conflict: stored=" + currentStatus + ", incoming=" + targetStatus)
                        .receivedAt(LocalDateTime.now())
                        .build();
                paymentEventRepository.saveAndFlush(event);

                auditLogService.logInvestigativeFailure(
                        AuditAction.PAYMENT_RESULT_CONFLICT,
                        "PAYMENT",
                        payment.getId().toString(),
                        Map.of(
                                "stored_status", currentStatus.name(),
                                "incoming_result", targetStatus.name(),
                                "payment_event_id", safeEventId(event)));

                return new ConvergenceTxResult(CallbackHandlingResult.CONFLICTING_RESULT, null);
            }

            if (outcome.providerTxnId() != null && !outcome.providerTxnId().isBlank()) {
                payment.setProviderTransactionId(outcome.providerTxnId());
            }
            if (outcome.source() == PaymentEventSource.IPN) {
                payment.setWebhookPayload(writePayload(outcome.rawPayload()));
                payment.setWebhookReceivedAt(LocalDateTime.now());
                payment.setWebhookSignatureValid(true);
            } else {
                payment.setProviderPayload(writePayload(outcome.rawPayload()));
            }

            if (targetStatus == PaymentStatus.PENDING) {
                payment.setNextReconcileAt(nextReconcileAt());
                paymentRepository.saveAndFlush(payment);
                return new ConvergenceTxResult(CallbackHandlingResult.PROCESSED, null);
            }

            if (targetStatus == PaymentStatus.FAILED || targetStatus == PaymentStatus.CANCELLED) {
                payment.setStatus(targetStatus);
                payment.setFailureCode(outcome.failureCode());
                String failureReason = outcome.failureMessage() != null ? outcome.failureMessage()
                        : (outcome.failureCode() != null ? "ResultCode=" + outcome.failureCode() : "Payment rejected by provider");
                payment.setFailureReason(failureReason);
                payment.setNextReconcileAt(null);
                Payment savedPayment = paymentRepository.saveAndFlush(payment);

                PaymentEvent event = PaymentEvent.builder()
                        .payment(savedPayment)
                        .provider(outcome.provider())
                        .eventSource(outcome.source() != null ? outcome.source() : PaymentEventSource.IPN)
                        .dedupeKey(outcome.dedupeKey())
                        .eventType(targetStatus.name())
                        .rawPayload(writePayload(outcome.rawPayload()))
                        .signatureValid(true)
                        .processingStatus(PaymentEventStatus.PROCESSED)
                        .receivedAt(LocalDateTime.now())
                        .processedAt(LocalDateTime.now())
                        .build();
                paymentEventRepository.saveAndFlush(event);

                auditLogService.logBusinessSuccess(
                        AuditAction.FAIL_PAYMENT,
                        "PAYMENT",
                        savedPayment.getId().toString(),
                        Map.of(
                                "new_status", targetStatus.name(),
                                "failure_code", outcome.failureCode() != null ? outcome.failureCode() : "",
                                "source", outcome.source().name()));

                eventPublisher.publishEvent(completedEvent(savedPayment));
                return new ConvergenceTxResult(CallbackHandlingResult.PROCESSED, null);
            }

            // SUCCESS / SUCCEEDED
            Order order = orderRepository.findByIdForUpdate(payment.getOrder().getId()).orElseThrow();
            boolean holdActive = order.getStatus() == OrderStatus.HELD
                    && order.getHoldExpiresAt() != null
                    && LocalDateTime.now().isBefore(order.getHoldExpiresAt());

            payment.setStatus(targetStatus == PaymentStatus.SUCCEEDED ? PaymentStatus.SUCCEEDED : PaymentStatus.SUCCESS);
            LocalDateTime paidAt = LocalDateTime.now();
            payment.setPaidAt(paidAt);
            payment.setNextReconcileAt(null);

            UUID refundId = null;

            if (holdActive) {
                orderInventoryService.settlePaidOrder(order.getId(), paidAt);
                Payment savedPayment = paymentRepository.saveAndFlush(payment);

                PaymentEvent event = PaymentEvent.builder()
                        .payment(savedPayment)
                        .provider(outcome.provider())
                        .eventSource(outcome.source() != null ? outcome.source() : PaymentEventSource.IPN)
                        .dedupeKey(outcome.dedupeKey())
                        .eventType("PAYMENT_SUCCESS")
                        .rawPayload(writePayload(outcome.rawPayload()))
                        .signatureValid(true)
                        .processingStatus(PaymentEventStatus.PROCESSED)
                        .receivedAt(LocalDateTime.now())
                        .processedAt(LocalDateTime.now())
                        .build();
                paymentEventRepository.saveAndFlush(event);

                auditLogService.logBusinessSuccess(
                        AuditAction.COMPLETE_PAYMENT,
                        "PAYMENT",
                        savedPayment.getId().toString(),
                        Map.of(
                                "order_id", order.getId().toString(),
                                "provider_txn_id", outcome.providerTxnId() != null ? outcome.providerTxnId() : "",
                                "source", outcome.source().name(),
                                "payment_event_id", safeEventId(event)));

                ticketIssuer.issueTickets(completedEvent(savedPayment));
            } else {
                payment.setFailureCode("LATE_PAYMENT_REFUND_INITIATED");
                payment.setFailureMessage("Thanh toán thành công sau khi hết hạn giữ chỗ. Hệ thống tự động hoàn tiền.");
                payment.setRefundRequired(true);
                Payment savedPayment = paymentRepository.saveAndFlush(payment);

                Refund refund = refundService.createRefund(
                        savedPayment,
                        savedPayment.getAmount(),
                        "Late payment after hold expiry",
                        "LATE_REFUND_" + savedPayment.getMerchantTxnRef(),
                        SYSTEM_ACTOR_UUID);
                refundId = refund != null ? refund.getId() : null;

                PaymentEvent event = PaymentEvent.builder()
                        .payment(savedPayment)
                        .provider(outcome.provider())
                        .eventSource(outcome.source() != null ? outcome.source() : PaymentEventSource.IPN)
                        .dedupeKey(outcome.dedupeKey())
                        .eventType("PAYMENT_SUCCESS")
                        .rawPayload(writePayload(outcome.rawPayload()))
                        .signatureValid(true)
                        .processingStatus(PaymentEventStatus.PROCESSED)
                        .receivedAt(LocalDateTime.now())
                        .processedAt(LocalDateTime.now())
                        .build();
                paymentEventRepository.saveAndFlush(event);

                auditLogService.logBusinessSuccess(
                        AuditAction.RECORD_LATE_PAYMENT,
                        "PAYMENT",
                        savedPayment.getId().toString(),
                        Map.of(
                                "order_id", order.getId().toString(),
                                "hold_expires_at", order.getHoldExpiresAt() != null ? order.getHoldExpiresAt().toString() : "",
                                "paid_at", paidAt.toString(),
                                "payment_event_id", safeEventId(event)));
            }

            return new ConvergenceTxResult(CallbackHandlingResult.PROCESSED, refundId);
        });

        if (txResult != null && txResult.refundIdToExecute() != null) {
            try {
                refundService.processRefund(txResult.refundIdToExecute());
            } catch (Exception ex) {
                log.error("Immediate refund processing failed for refund {}: {}", txResult.refundIdToExecute(), ex.getMessage());
            }
        }

        return txResult != null ? txResult.result() : CallbackHandlingResult.PROCESSED;
    }

    private boolean isTerminalStatus(PaymentStatus status) {
        return status == PaymentStatus.SUCCESS || status == PaymentStatus.SUCCEEDED
                || status == PaymentStatus.FAILED || status == PaymentStatus.CANCELLED
                || status == PaymentStatus.EXPIRED;
    }

    private boolean isSameTerminalStatus(PaymentStatus current, PaymentStatus target) {
        if ((current == PaymentStatus.SUCCESS || current == PaymentStatus.SUCCEEDED)
                && (target == PaymentStatus.SUCCESS || target == PaymentStatus.SUCCEEDED)) {
            return true;
        }
        return current == target;
    }

    private Payment findPaymentOrNull(String reference) {
        if (reference == null || reference.isBlank()) {
            return null;
        }
        Optional<Payment> payment = paymentRepository.findByMerchantTxnRef(reference);
        if (payment.isPresent()) {
            return payment.get();
        }
        try {
            UUID id = UUID.fromString(reference);
            return paymentRepository.findById(id).orElse(null);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Optional<Payment> findPaymentForUpdate(String reference) {
        if (reference == null || reference.isBlank()) {
            return Optional.empty();
        }
        Optional<Payment> payment = paymentRepository.findByMerchantTxnRefForUpdate(reference);
        if (payment.isPresent()) {
            return payment;
        }
        try {
            UUID id = UUID.fromString(reference);
            return paymentRepository.findByIdForUpdate(id);
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private CreatePaymentResponse createNewPayment(User currentUser, String idempotencyKey,
            CreatePaymentRequest request, String clientIp) {
        PaymentGatewayStrategy gateway = resolveGateway(request.getProvider());

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
        if (paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.PENDING)
                || paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.CREATING)) {
            throw new AppException(HttpStatus.CONFLICT, "PAYMENT_IN_PROGRESS",
                    "Order already has a payment in progress");
        }
        if (paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCESS)
                || paymentRepository.existsByOrderIdAndStatus(order.getId(), PaymentStatus.SUCCEEDED)) {
            throw new AppException(HttpStatus.CONFLICT, "ORDER_ALREADY_PAID", "Order has already been paid");
        }

        String merchantTxnRef = "TBX_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        Payment payment = paymentRepository.saveAndFlush(Payment.builder()
                .order(order)
                .provider(request.getProvider())
                .merchantTxnRef(merchantTxnRef)
                .idempotencyKey(idempotencyKey)
                .amount(order.getTotalAmount())
                .currency(order.getCurrency())
                .status(request.getProvider() == PaymentProvider.MOMO ? PaymentStatus.CREATING : PaymentStatus.PENDING)
                .nextReconcileAt(request.getProvider() == PaymentProvider.MOMO ? nextReconcileAt() : null)
                .build());

        auditLogService.logBusinessSuccess(
                AuditAction.CREATE_PAYMENT,
                "PAYMENT",
                payment.getId().toString(),
                Map.of(
                        "order_id", order.getId().toString(),
                        "provider", payment.getProvider().name(),
                        "merchant_txn_ref", payment.getMerchantTxnRef(),
                        "amount", payment.getAmount().toString()));

        return payment;
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
            PaymentOutcome outcome = PaymentOutcome.builder()
                    .provider(payment.getProvider())
                    .source(PaymentEventSource.QUERY_API)
                    .dedupeKey(result.providerTransactionId() != null
                            ? result.providerTransactionId() + "_" + result.status()
                            : paymentId + "_QUERY")
                    .merchantTxnRef(payment.getMerchantTxnRef() != null ? payment.getMerchantTxnRef() : paymentId.toString())
                    .providerTxnId(result.providerTransactionId())
                    .targetStatus(result.status())
                    .amount(result.amount())
                    .signatureValid(true)
                    .failureCode(result.resultCode())
                    .rawPayload(Map.of("source", "query", "resultCode", result.resultCode() != null ? result.resultCode() : ""))
                    .build();
            processPaymentResult(outcome);
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

    private String writePayload(Object payload) {
        if (payload == null) {
            return "{}";
        }
        if (payload instanceof String str) {
            return str;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return "{}";
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
        return paymentMapper.toCreatePaymentResponse(payment);
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

    private String safeEventId(PaymentEvent event) {
        return event != null && event.getId() != null ? event.getId().toString() : "";
    }
}
