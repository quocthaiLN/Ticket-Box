package com.ticketbox.api.module.inventory.repositories;

import com.ticketbox.api.module.inventory.domain.entities.UserTicketTypeCounter;
import com.ticketbox.api.module.inventory.domain.entities.UserTicketTypeCounterId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTicketTypeCounterRepository extends JpaRepository<UserTicketTypeCounter, UserTicketTypeCounterId> {

}





