package com.ticketbox.api.checkin.repositories;

import com.ticketbox.api.checkin.domain.entities.CheckinGateZone;
import com.ticketbox.api.checkin.domain.entities.CheckinGateZoneId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CheckinGateZoneRepository extends JpaRepository<CheckinGateZone, CheckinGateZoneId> {

}





