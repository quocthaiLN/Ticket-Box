package com.ticketbox.api.checkin.repositories;

import com.ticketbox.api.checkin.domain.entities.CheckinLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CheckinLogRepository extends JpaRepository<CheckinLog, UUID> {

}





