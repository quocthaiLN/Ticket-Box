package com.ticketbox.api.module.order.repositories;

import com.ticketbox.api.module.order.domain.entities.UserTicketTypeCounter;
import com.ticketbox.api.module.order.domain.entities.UserTicketTypeCounterId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserTicketTypeCounterRepository extends JpaRepository<UserTicketTypeCounter, UserTicketTypeCounterId> {

}
