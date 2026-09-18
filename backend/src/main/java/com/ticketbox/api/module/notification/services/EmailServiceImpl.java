package com.ticketbox.api.module.notification.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.ticketbox.api.module.shared.domain.dtos.AuthOtpMessageDTO;
import com.ticketbox.api.module.payment.events.PaymentCompletedEvent;
import com.ticketbox.api.module.ticket.events.TicketIssuedEvent;

import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@ticketbox.com}")
    private String fromEmail;

    @Value("${app.frontend.base-url:http://localhost:3001}")
    private String frontendBaseUrl;

    @Override
    public void sendOtpEmail(AuthOtpMessageDTO message) {
        log.info("Sending OTP email to recipient: {}", message.getEmail());

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(fromEmail);
            helper.setTo(message.getEmail());
            helper.setSubject("[TicketBox] Mã OTP xác thực tài khoản");

            String htmlContent = String.format("""
                <div style="font-family: Arial, sans-serif; padding: 20px; color: #333; max-width: 600px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 8px;">
                    <h2 style="color: #4F46E5; text-align: center;">TicketBox - Mã Xác Thực OTP</h2>
                    <p>Xin chào,</p>
                    <p>Cảm ơn bạn đã đăng ký tài khoản tại <strong>TicketBox</strong>. Mã OTP xác thực của bạn là:</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <span style="font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #4F46E5; background-color: #EEF2FF; padding: 12px 24px; border-radius: 6px; display: inline-block;">
                            %s
                        </span>
                    </div>
                    <p>Mã OTP này có hiệu lực trong vòng vài phút. Vui lòng không chia sẻ mã này với bất kỳ ai.</p>
                    <hr style="border: none; border-top: 1px solid #eee; margin: 20px 0;" />
                    <p style="font-size: 12px; color: #777; text-align: center;">Trân trọng,<br/>Đội ngũ TicketBox</p>
                </div>
                """, message.getOtp());

            helper.setText(htmlContent, true);

            mailSender.send(mimeMessage);
            log.info("Successfully sent OTP email to: {}", message.getEmail());
        } catch (MessagingException e) {
            log.error("Failed to send OTP email to: {}", message.getEmail(), e);
            throw new RuntimeException("Error occurred while sending OTP email", e);
        }
    }

    @Override
    public void sendPaymentFailedEmail(String recipientEmail, PaymentCompletedEvent event) {
        sendHtmlEmail(recipientEmail,
                "[TicketBox] Thanh toán không thành công",
                String.format("""
                        <p>Thanh toán cho đơn hàng <strong>%s</strong> không thành công.</p>
                        <p>Bạn có thể thử thanh toán lại trước khi thời gian giữ chỗ kết thúc.</p>
                        """, event.orderId()),
                "payment-failed");
    }

    @Override
    public void sendTicketIssuedEmail(String recipientEmail, TicketIssuedEvent event) {
        String ticketLinks = event.ticketIds().stream()
                .map(ticketId -> String.format("""
                        <div style="margin: 16px 0; padding: 12px; border: 1px solid #e0e0e0; border-radius: 6px;">
                          <p style="margin: 0 0 8px;"><strong>Vé %s</strong></p>
                          <a href="%s" style="color: #4F46E5; font-weight: 600;">Xem mã QR của vé</a>
                        </div>
                        """, HtmlUtils.htmlEscape(ticketId.toString()), HtmlUtils.htmlEscape(ticketUrl(ticketId))))
                .collect(Collectors.joining());
        sendHtmlEmail(recipientEmail,
                "[TicketBox] Vé của bạn đã được phát hành",
                String.format("""
                        <p>Đơn hàng <strong>%s</strong> đã được xác nhận.</p>
                        <p>%d vé đã được phát hành. Đăng nhập TicketBox để xem mã QR của từng vé.</p>
                        %s
                        """, HtmlUtils.htmlEscape(event.orderId().toString()), event.ticketIds().size(), ticketLinks),
                "ticket-issued");
    }

    private String ticketUrl(java.util.UUID ticketId) {
        return UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .pathSegment("my-tickets", ticketId.toString())
                .toUriString();
    }

    private void sendHtmlEmail(String recipientEmail, String subject, String htmlContent, String emailType) {
        log.info("Sending {} email to recipient: {}", emailType, recipientEmail);
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail);
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(mimeMessage);
            log.info("Successfully sent {} email to: {}", emailType, recipientEmail);
        } catch (MessagingException e) {
            log.error("Failed to send {} email to: {}", emailType, recipientEmail, e);
            throw new RuntimeException("Error occurred while sending " + emailType + " email", e);
        }
    }
}
