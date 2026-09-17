package com.ticketbox.api.module.payment.domain.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ticketbox.api.module.payment.domain.entities.PaymentProvider;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreatePaymentRequest {

    @NotNull(message = "Order ID is required")
    @JsonProperty("order_id")
    private UUID orderId;

    @NotNull(message = "Payment provider is required")
    private PaymentProvider provider;

    @Pattern(regexp = "^(VNBANK|VNPAYQR|INTCARD)$", message = "Bank code is not supported")
    @JsonProperty("bank_code")
    private String bankCode;

    @Pattern(regexp = "^(vn|en)$", message = "Locale must be vn or en")
    private String locale;
}
