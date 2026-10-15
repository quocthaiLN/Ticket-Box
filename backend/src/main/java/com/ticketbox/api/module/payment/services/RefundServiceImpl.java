package com.ticketbox.api.module.payment.services;

import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.module.audit.domain.entities.AuditAction;
import com.ticketbox.api.module.audit.services.AuditLogService;
import com.ticketbox.api.module.payment.domain.dtos.RefundGatewayRequest;
import com.ticketbox.api.module.payment.domain.dtos.RefundGatewayResult;
import com.ticketbox.api.module.payment.domain.dtos.RefundResponse;
import com.ticketbox.api.module.payment.domain.entities.Payment;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.Refund;
import com.ticketbox.api.module.payment.domain.entities.RefundStatus;
import com.ticketbox.api.module.payment.domain.mappers.RefundMapper;
import com.ticketbox.api.module.payment.gateways.PaymentRefundGateway;
import com.ticketbox.api.module.payment.repositories.RefundRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class RefundServiceImpl implements RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundServiceImpl.class);

    private final RefundRepository refundRepository;
    private final List<PaymentRefundGateway> refundGateways;
    private final RefundMapper refundMapper;
    private final AuditLogService auditLogService;
    private final TransactionTemplate transactionTemplate;

    public RefundServiceImpl(
            RefundRepository refundRepository,
            List<PaymentRefundGateway> refundGateways,
            RefundMapper refundMapper,
            AuditLogService auditLogService,
            TransactionTemplate transactionTemplate) {
        this.refundRepository = refundRepository;
        this.refundGateways = refundGateways;
        this.refundMapper = refundMapper;
        this.auditLogService = auditLogService;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    @Transactional
    public Refund createRefund(
            Payment payment,
            BigDecimal amount,
            String reason,
            String idempotencyKey,
            UUID requestedBy) {
        Optional<Refund> existing = refundRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        Refund refund = Refund.builder()
                .payment(payment)
                .amount(amount)
                .currency(payment.getCurrency())
                .idempotencyKey(idempotencyKey)
                .status(RefundStatus.REQUESTED)
                .reason(reason)
                .requestedBy(requestedBy)
                .build();

        Refund savedRefund = refundRepository.saveAndFlush(refund);

        auditLogService.logBusinessSuccess(
                AuditAction.REQUEST_REFUND,
                "REFUND",
                savedRefund.getId().toString(),
                Map.of(
                        "payment_id", payment.getId().toString(),
                        "amount", amount.toString(),
                        "reason", reason != null ? reason : "",
                        "idempotency_key", idempotencyKey));

        return savedRefund;
    }

    @Override
    public RefundResponse processRefund(UUID refundId) {
        Boolean claimed = transactionTemplate.execute(status -> {
            int updated = refundRepository.updateStatusIfExpected(refundId, RefundStatus.REQUESTED, RefundStatus.PROCESSING);
            return updated > 0;
        });

        Refund refund = refundRepository.findWithPaymentById(refundId)
                .or(() -> refundRepository.findById(refundId))
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "REFUND_NOT_FOUND", "Refund not found"));

        if (!Boolean.TRUE.equals(claimed) && refund.getStatus() != RefundStatus.PROCESSING) {
            log.info("Refund {} is already in terminal/processing state: {}", refundId, refund.getStatus());
            return refundMapper.toResponse(refund);
        }

        Payment payment = refund.getPayment();
        PaymentRefundGateway gateway = resolveGateway(payment.getProvider());

        LocalDateTime transDateTime = payment.getPaidAt() != null ? payment.getPaidAt() : payment.getCreatedAt();
        String transDateStr = transDateTime != null
                ? transDateTime.format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                : null;

        RefundGatewayRequest gatewayRequest = RefundGatewayRequest.builder()
                .refundId(refund.getId())
                .merchantTxnRef(payment.getMerchantTxnRef())
                .providerTxnId(payment.getProviderTransactionId())
                .amount(refund.getAmount())
                .reason(refund.getReason())
                .transDate(transDateStr)
                .build();

        RefundGatewayResult result;
        try {
            result = gateway.refund(gatewayRequest);
        } catch (Exception exception) {
            log.error("Unexpected exception during refund call for refund {}: {}", refundId, exception.getMessage(), exception);
            result = RefundGatewayResult.builder()
                    .status(RefundStatus.PROCESSING)
                    .responseCode("GATEWAY_ERROR")
                    .message(exception.getMessage())
                    .build();
        }

        final RefundGatewayResult finalResult = result;
        return transactionTemplate.execute(status -> {
            Refund r = refundRepository.findByIdForUpdate(refundId).orElseThrow();
            if (finalResult.providerRefundId() != null && !finalResult.providerRefundId().isBlank()) {
                r.setProviderRefundId(finalResult.providerRefundId());
            }
            if (finalResult.status() == RefundStatus.SUCCESS) {
                r.setStatus(RefundStatus.SUCCESS);
                r.setCompletedAt(LocalDateTime.now());
                r.setFailureCode(null);
                r.setFailureMessage(null);
                refundRepository.saveAndFlush(r);

                auditLogService.logBusinessSuccess(
                        AuditAction.COMPLETE_REFUND,
                        "REFUND",
                        r.getId().toString(),
                        Map.of(
                                "payment_id", r.getPayment().getId().toString(),
                                "provider_refund_id", r.getProviderRefundId() != null ? r.getProviderRefundId() : "",
                                "amount", r.getAmount().toString()));
            } else if (finalResult.status() == RefundStatus.FAILED) {
                r.setStatus(RefundStatus.FAILED);
                r.setFailureCode(finalResult.responseCode());
                r.setFailureMessage(finalResult.message());
                r.setCompletedAt(LocalDateTime.now());
                refundRepository.saveAndFlush(r);

                auditLogService.logBusinessSuccess(
                        AuditAction.FAIL_REFUND,
                        "REFUND",
                        r.getId().toString(),
                        Map.of(
                                "payment_id", r.getPayment().getId().toString(),
                                "failure_code", finalResult.responseCode() != null ? finalResult.responseCode() : "FAILED",
                                "message", finalResult.message() != null ? finalResult.message() : ""));
            } else {
                r.setStatus(RefundStatus.PROCESSING);
                r.setFailureCode(finalResult.responseCode());
                r.setFailureMessage(finalResult.message());
                refundRepository.saveAndFlush(r);
            }
            return refundMapper.toResponse(r);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<RefundResponse> getRefundsByPaymentId(UUID paymentId) {
        return refundRepository.findByPaymentIdOrderByRequestedAtAsc(paymentId).stream()
                .map(refundMapper::toResponse)
                .toList();
    }

    private PaymentRefundGateway resolveGateway(PaymentProvider provider) {
        return refundGateways.stream()
                .filter(gateway -> gateway.getProvider() == provider)
                .findFirst()
                .orElseThrow(() -> new AppException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "REFUND_GATEWAY_NOT_SUPPORTED",
                        "Refund is not supported for provider " + provider));
    }
}
