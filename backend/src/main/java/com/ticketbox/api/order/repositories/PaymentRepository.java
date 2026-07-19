package com.ticketbox.api.order.repositories;

import com.ticketbox.api.order.domain.entities.Payment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

}




