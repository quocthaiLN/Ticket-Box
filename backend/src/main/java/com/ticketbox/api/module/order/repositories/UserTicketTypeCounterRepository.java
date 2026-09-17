package com.ticketbox.api.module.order.repositories;

import com.ticketbox.api.module.order.domain.entities.UserTicketTypeCounter;
import com.ticketbox.api.module.order.domain.entities.UserTicketTypeCounterId;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Repository
public interface UserTicketTypeCounterRepository extends JpaRepository<UserTicketTypeCounter, UserTicketTypeCounterId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT c FROM UserTicketTypeCounter c
            WHERE c.id.userId = :userId AND c.id.ticketTypeId = :ticketTypeId
            """)
    Optional<UserTicketTypeCounter> findByUserIdAndTicketTypeIdForUpdate(
            @Param("userId") UUID userId,
            @Param("ticketTypeId") UUID ticketTypeId);

    @Query("""
            SELECT c FROM UserTicketTypeCounter c
            WHERE c.id.userId = :userId AND c.ticketType.concert.id = :concertId
            """)
    List<UserTicketTypeCounter> findByUserIdAndConcertId(
            @Param("userId") UUID userId,
            @Param("concertId") UUID concertId);
}
