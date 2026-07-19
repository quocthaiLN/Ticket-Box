package com.ticketbox.api.user.repositories;

import com.ticketbox.api.user.domain.entities.UserTicketTypeCounter;
import com.ticketbox.api.user.domain.entities.UserTicketTypeCounterId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTicketTypeCounterRepository extends JpaRepository<UserTicketTypeCounter, UserTicketTypeCounterId> {

}





