package com.ticketbox.api.module.notification.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import com.ticketbox.api.module.payment.domain.entities.PaymentStatus;
import com.ticketbox.api.module.payment.events.PaymentCompletedEvent;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

class EmailServiceImplTest {
    @ParameterizedTest
    @EnumSource(value = PaymentStatus.class, names = {"FAILED", "CANCELLED"})
    void sendsDistinctPaymentOutcomeEmail(PaymentStatus status) throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(sender.createMimeMessage()).thenReturn(message);
        EmailServiceImpl service = new EmailServiceImpl(sender);
        ReflectionTestUtils.setField(service, "fromEmail", "tickets@example.test");
        UUID orderId = UUID.randomUUID();
        service.sendPaymentFailedEmail("buyer@example.test", new PaymentCompletedEvent(UUID.randomUUID(), orderId,
                UUID.randomUUID(), PaymentProvider.MOMO, status, BigDecimal.TEN, "VND", "12345"));
        verify(sender).send(message);
        boolean cancelled = status == PaymentStatus.CANCELLED;
        assertThat(message.getSubject()).isEqualTo(cancelled
                ? "[TicketBox] Thanh toán đã bị hủy" : "[TicketBox] Thanh toán không thành công");
        assertThat(text(message)).contains(orderId.toString(), cancelled ? "đã bị hủy" : "không thành công",
                "trước khi thời gian giữ chỗ kết thúc");
    }

    private String text(Part part) throws Exception {
        Object content = part.getContent();
        if (content instanceof Multipart multipart) {
            StringBuilder result = new StringBuilder();
            for (int i = 0; i < multipart.getCount(); i++) result.append(text(multipart.getBodyPart(i)));
            return result.toString();
        }
        return content.toString();
    }
}
