package com.ticketbox.api.module.inventory;

import com.ticketbox.api.module.inventory.UserTicketTypeCounter;
import com.ticketbox.api.module.inventory.UserTicketTypeCounterId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTicketTypeCounterRepository extends JpaRepository<UserTicketTypeCounter, UserTicketTypeCounterId> {

}





