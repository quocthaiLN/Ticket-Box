package com.ticketbox.api.module.payment.services;

import com.ticketbox.api.module.audit.domain.entities.AuditAction;
import com.ticketbox.api.module.audit.services.AuditLogService;
import com.ticketbox.api.module.payment.domain.dtos.RefundGatewayRequest;
import com.ticketbox.api.module.payment.domain.dtos.RefundGatewayResult;
import com.ticketbox.api.module.payment.domain.dtos.RefundResponse;
import com.ticketbox.api.module.payment.domain.entities.Payment;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import com.ticketbox.api.module.payment.domain.entities.Refund;
import com.ticketbox.api.module.payment.domain.entities.RefundStatus;
import com.ticketbox.api.module.payment.domain.mappers.RefundMapperImpl;
import com.ticketbox.api.module.payment.gateways.PaymentRefundGateway;
import com.ticketbox.api.module.payment.repositories.RefundRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private PaymentRefundGateway momoRefundGateway;

    @Mock
    private PaymentRefundGateway vnpayRefundGateway;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private TransactionStatus transactionStatus;

    private RefundServiceImpl refundService;

    private Payment testPayment;
    private final UUID refundId = UUID.randomUUID();
    private final UUID paymentId = UUID.randomUUID();
    private final UUID systemActorId = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @BeforeEach
    void setUp() {
        refundService = new RefundServiceImpl(
                refundRepository,
                List.of(momoRefundGateway, vnpayRefundGateway),
                new RefundMapperImpl(),
                auditLogService,
                transactionTemplate);

        lenient().when(momoRefundGateway.getProvider()).thenReturn(PaymentProvider.MOMO);
        lenient().when(vnpayRefundGateway.getProvider()).thenReturn(PaymentProvider.VNPAY);

        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });

        testPayment = Payment.builder()
                .id(paymentId)
                .provider(PaymentProvider.MOMO)
                .merchantTxnRef("TBX_REF_123456")
                .providerTransactionId("MOMO_TXN_999")
                .amount(new BigDecimal("250000.00"))
                .currency("VND")
                .status(PaymentStatus.SUCCESS)
                .paidAt(LocalDateTime.now().minusMinutes(5))
                .build();
    }

    @Test
    @DisplayName("createRefund creates a new refund in REQUESTED status and logs business audit")
    void testCreateRefund_success() {
        when(refundRepository.findByIdempotencyKey("LATE_REFUND_TBX_REF_123456"))
                .thenReturn(Optional.empty());

        when(refundRepository.saveAndFlush(any(Refund.class))).thenAnswer(invocation -> {
            Refund r = invocation.getArgument(0);
            r.setId(refundId);
            return r;
        });

        Refund refund = refundService.createRefund(
                testPayment,
                new BigDecimal("250000.00"),
                "Late payment after hold expiry",
                "LATE_REFUND_TBX_REF_123456",
                systemActorId);

        assertNotNull(refund);
        assertEquals(RefundStatus.REQUESTED, refund.getStatus());
        assertEquals(new BigDecimal("250000.00"), refund.getAmount());
        assertEquals("LATE_REFUND_TBX_REF_123456", refund.getIdempotencyKey());

        verify(auditLogService).logBusinessSuccess(
                eq(AuditAction.REQUEST_REFUND),
                eq("REFUND"),
                eq(refundId.toString()),
                any());
    }

    @Test
    @DisplayName("createRefund returns existing refund without re-creating if idempotencyKey exists")
    void testCreateRefund_idempotent() {
        Refund existingRefund = Refund.builder()
                .id(refundId)
                .payment(testPayment)
                .amount(new BigDecimal("250000.00"))
                .currency("VND")
                .idempotencyKey("LATE_REFUND_TBX_REF_123456")
                .status(RefundStatus.REQUESTED)
                .build();

        when(refundRepository.findByIdempotencyKey("LATE_REFUND_TBX_REF_123456"))
                .thenReturn(Optional.of(existingRefund));

        Refund result = refundService.createRefund(
                testPayment,
                new BigDecimal("250000.00"),
                "Late payment after hold expiry",
                "LATE_REFUND_TBX_REF_123456",
                systemActorId);

        assertEquals(existingRefund, result);
        verify(refundRepository, never()).saveAndFlush(any());
        verify(auditLogService, never()).logBusinessSuccess(any(), any(), any(), any());
    }

    @Test
    @DisplayName("processRefund successfully claims, invokes gateway, and completes refund")
    void testProcessRefund_gatewaySuccess() {
        Refund refund = Refund.builder()
                .id(refundId)
                .payment(testPayment)
                .amount(new BigDecimal("250000.00"))
                .currency("VND")
                .idempotencyKey("LATE_REFUND_TBX_REF_123456")
                .status(RefundStatus.REQUESTED)
                .reason("Late payment")
                .build();

        when(refundRepository.updateStatusIfExpected(refundId, RefundStatus.REQUESTED, RefundStatus.PROCESSING))
                .thenReturn(1);
        when(refundRepository.findById(refundId)).thenReturn(Optional.of(refund));
        when(refundRepository.findByIdForUpdate(refundId)).thenReturn(Optional.of(refund));

        when(momoRefundGateway.refund(any(RefundGatewayRequest.class))).thenReturn(
                RefundGatewayResult.builder()
                        .status(RefundStatus.SUCCESS)
                        .providerRefundId("MOMO_REF_001")
                        .responseCode("0")
                        .message("Refund success")
                        .build());

        when(refundRepository.saveAndFlush(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefundResponse response = refundService.processRefund(refundId);

        assertEquals(RefundStatus.SUCCESS, response.status());
        assertEquals("MOMO_REF_001", response.providerRefundId());
        assertEquals(new BigDecimal("250000.00"), response.amount());

        verify(auditLogService).logBusinessSuccess(
                eq(AuditAction.COMPLETE_REFUND),
                eq("REFUND"),
                eq(refundId.toString()),
                any());
    }

    @Test
    @DisplayName("processRefund gateway timeout keeps refund in PROCESSING status")
    void testProcessRefund_gatewayTimeout() {
        Refund refund = Refund.builder()
                .id(refundId)
                .payment(testPayment)
                .amount(new BigDecimal("250000.00"))
                .currency("VND")
                .idempotencyKey("LATE_REFUND_TBX_REF_123456")
                .status(RefundStatus.REQUESTED)
                .build();

        when(refundRepository.updateStatusIfExpected(refundId, RefundStatus.REQUESTED, RefundStatus.PROCESSING))
                .thenReturn(1);
        when(refundRepository.findById(refundId)).thenReturn(Optional.of(refund));
        when(refundRepository.findByIdForUpdate(refundId)).thenReturn(Optional.of(refund));

        when(momoRefundGateway.refund(any(RefundGatewayRequest.class))).thenReturn(
                RefundGatewayResult.builder()
                        .status(RefundStatus.PROCESSING)
                        .responseCode("GATEWAY_TIMEOUT")
                        .message("Gateway call timed out")
                        .build());

        when(refundRepository.saveAndFlush(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefundResponse response = refundService.processRefund(refundId);

        assertEquals(RefundStatus.PROCESSING, response.status());
        verify(auditLogService, never()).logBusinessSuccess(eq(AuditAction.COMPLETE_REFUND), any(), any(), any());
        verify(auditLogService, never()).logBusinessSuccess(eq(AuditAction.FAIL_REFUND), any(), any(), any());
    }

    @Test
    @DisplayName("processRefund gateway rejection marks refund FAILED and logs audit")
    void testProcessRefund_gatewayRejection() {
        Refund refund = Refund.builder()
                .id(refundId)
                .payment(testPayment)
                .amount(new BigDecimal("250000.00"))
                .currency("VND")
                .idempotencyKey("LATE_REFUND_TBX_REF_123456")
                .status(RefundStatus.REQUESTED)
                .build();

        when(refundRepository.updateStatusIfExpected(refundId, RefundStatus.REQUESTED, RefundStatus.PROCESSING))
                .thenReturn(1);
        when(refundRepository.findById(refundId)).thenReturn(Optional.of(refund));
        when(refundRepository.findByIdForUpdate(refundId)).thenReturn(Optional.of(refund));

        when(momoRefundGateway.refund(any(RefundGatewayRequest.class))).thenReturn(
                RefundGatewayResult.builder()
                        .status(RefundStatus.FAILED)
                        .responseCode("1004")
                        .message("User account locked")
                        .build());

        when(refundRepository.saveAndFlush(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefundResponse response = refundService.processRefund(refundId);

        assertEquals(RefundStatus.FAILED, response.status());
        verify(auditLogService).logBusinessSuccess(
                eq(AuditAction.FAIL_REFUND),
                eq("REFUND"),
                eq(refundId.toString()),
                any());
    }

    @Test
    @DisplayName("processRefund persists providerRefundId even when status is FAILED")
    void testProcessRefund_gatewayFailedWithProviderRefundId() {
        Refund refund = Refund.builder()
                .id(refundId)
                .payment(testPayment)
                .amount(new BigDecimal("250000.00"))
                .currency("VND")
                .idempotencyKey("LATE_REFUND_TBX_REF_123456")
                .status(RefundStatus.REQUESTED)
                .build();

        when(refundRepository.updateStatusIfExpected(refundId, RefundStatus.REQUESTED, RefundStatus.PROCESSING))
                .thenReturn(1);
        when(refundRepository.findById(refundId)).thenReturn(Optional.of(refund));
        when(refundRepository.findByIdForUpdate(refundId)).thenReturn(Optional.of(refund));

        when(momoRefundGateway.refund(any(RefundGatewayRequest.class))).thenReturn(
                RefundGatewayResult.builder()
                        .providerRefundId("MOMO_REF_FAILED_001")
                        .status(RefundStatus.FAILED)
                        .responseCode("TXN_STATUS_02")
                        .message("Gateway rejected")
                        .build());

        when(refundRepository.saveAndFlush(any(Refund.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefundResponse response = refundService.processRefund(refundId);

        assertEquals(RefundStatus.FAILED, response.status());
        assertEquals("MOMO_REF_FAILED_001", response.providerRefundId());
    }

    @Test
    @DisplayName("processRefund when already claimed or in processing status skips gateway invocation")
    void testProcessRefund_whenAlreadyClaimed_skipsGateway() {
        Refund refund = Refund.builder()
                .id(refundId)
                .payment(testPayment)
                .amount(new BigDecimal("250000.00"))
                .currency("VND")
                .idempotencyKey("LATE_REFUND_TBX_REF_123456")
                .status(RefundStatus.PROCESSING)
                .build();

        when(refundRepository.updateStatusIfExpected(refundId, RefundStatus.REQUESTED, RefundStatus.PROCESSING))
                .thenReturn(0);
        when(refundRepository.findWithPaymentById(refundId)).thenReturn(Optional.of(refund));

        RefundResponse response = refundService.processRefund(refundId);

        assertEquals(RefundStatus.PROCESSING, response.status());
        verify(momoRefundGateway, never()).refund(any());
        verify(vnpayRefundGateway, never()).refund(any());
    }
}
