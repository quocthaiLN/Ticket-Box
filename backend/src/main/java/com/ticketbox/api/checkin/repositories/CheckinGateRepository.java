package com.ticketbox.api.checkin.repositories;

import com.ticketbox.api.checkin.domain.entities.CheckinGate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CheckinGateRepository extends JpaRepository<CheckinGate, UUID> {

}





