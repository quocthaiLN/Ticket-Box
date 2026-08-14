package com.ticketbox.api.module.notification.services;

import com.ticketbox.api.module.shared.domain.dtos.AuthOtpMessageDTO;

public interface EmailService {

    void sendOtpEmail(AuthOtpMessageDTO message);
}
