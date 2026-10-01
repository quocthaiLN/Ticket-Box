package com.ticketbox.api.module.payment.domain.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.LinkedHashMap;
import java.util.Map;

public record MomoIpnRequest(
        @NotBlank String orderType,
        @NotNull @Positive Long amount,
        @NotBlank String partnerCode,
        @NotBlank String orderId,
        @NotNull String extraData,
        @NotBlank String signature,
        @NotNull @Positive Long transId,
        @NotNull @Positive Long responseTime,
        @NotNull Integer resultCode,
        @NotBlank String message,
        @NotNull String payType,
        @NotBlank String requestId,
        @NotBlank String orderInfo) {

    public Map<String, String> toParameters() {
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("orderType", orderType);
        parameters.put("amount", amount.toString());
        parameters.put("partnerCode", partnerCode);
        parameters.put("orderId", orderId);
        parameters.put("extraData", extraData);
        parameters.put("signature", signature);
        parameters.put("transId", transId.toString());
        parameters.put("responseTime", responseTime.toString());
        parameters.put("resultCode", resultCode.toString());
        parameters.put("message", message);
        parameters.put("payType", payType);
        parameters.put("requestId", requestId);
        parameters.put("orderInfo", orderInfo);
        return parameters;
    }
}
