package com.ticketbox.api.module.notification.services;

import com.ticketbox.api.module.share.dtos.AuthOtpMessageDTO;

public interface EmailService {

    void sendOtpEmail(AuthOtpMessageDTO message);
}
