package com.ticketbox.api.module.share.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthOtpMessageDTO {

    private String userId;
    private String email;
    private String otp;
    private String otpType;
    private long expirationTime;
}

