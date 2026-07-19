package com.ticketbox.api.checkin.repositories;

import com.ticketbox.api.checkin.domain.entities.CheckinGateStaff;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CheckinGateStaffRepository extends JpaRepository<CheckinGateStaff, UUID> {

}





