package com.ticketbox.api.module.payment.domain.dtos;

import java.io.Serializable;
import java.util.UUID;

public record RefundProcessMessage(UUID refundId) implements Serializable {
}
