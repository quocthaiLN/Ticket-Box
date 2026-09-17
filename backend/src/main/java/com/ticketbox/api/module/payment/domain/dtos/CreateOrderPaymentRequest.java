package com.ticketbox.api.module.payment.domain.dtos;

import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateOrderPaymentRequest {

    @NotNull(message = "Payment provider is required")
    private PaymentProvider provider;

    @Pattern(regexp = "^(VNBANK|VNPAYQR|INTCARD)$", message = "Bank code is not supported")
    private String bankCode;

    @Pattern(regexp = "^(vn|en)$", message = "Locale must be vn or en")
    private String locale;
}
