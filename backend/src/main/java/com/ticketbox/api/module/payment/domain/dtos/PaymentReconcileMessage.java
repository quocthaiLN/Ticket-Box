package com.ticketbox.api.module.payment.domain.dtos;

import java.io.Serializable;
import java.util.UUID;

public record PaymentReconcileMessage(UUID paymentId) implements Serializable {
}
