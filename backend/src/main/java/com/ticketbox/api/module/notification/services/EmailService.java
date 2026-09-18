package com.ticketbox.api.module.notification.services;

import com.ticketbox.api.module.shared.domain.dtos.AuthOtpMessageDTO;
import com.ticketbox.api.module.payment.events.PaymentCompletedEvent;
import com.ticketbox.api.module.ticket.events.TicketIssuedEvent;

public interface EmailService {

    void sendOtpEmail(AuthOtpMessageDTO message);

    void sendPaymentFailedEmail(String recipientEmail, PaymentCompletedEvent event);

    void sendTicketIssuedEmail(String recipientEmail, TicketIssuedEvent event);
}
