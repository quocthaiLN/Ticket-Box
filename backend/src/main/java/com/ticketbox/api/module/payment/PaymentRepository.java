package com.ticketbox.api.module.payment;

import com.ticketbox.api.module.payment.Payment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

}




