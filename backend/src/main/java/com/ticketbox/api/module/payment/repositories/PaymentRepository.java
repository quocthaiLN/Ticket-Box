package com.ticketbox.api.module.payment.repositories;

import com.ticketbox.api.module.payment.domain.entities.Payment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

}




