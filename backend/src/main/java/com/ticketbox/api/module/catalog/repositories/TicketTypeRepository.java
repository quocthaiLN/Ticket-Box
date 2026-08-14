package com.ticketbox.api.module.catalog.repositories;

import com.ticketbox.api.module.catalog.domain.entities.TicketType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TicketTypeRepository extends JpaRepository<TicketType, UUID> {

    List<TicketType> findByConcertId(UUID concertId);

    List<TicketType> findByConcertIdAndSeatZoneId(UUID concertId, UUID seatZoneId);

    boolean existsByConcertIdAndName(UUID concertId, String name);

    Optional<TicketType> findByIdAndConcertId(UUID id, UUID concertId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TicketType t WHERE t.id = :id")
    Optional<TicketType> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT COALESCE(SUM(t.totalQuantity), 0) FROM TicketType t WHERE t.concert.id = :concertId AND t.seatZone.id = :seatZoneId")
    Integer sumTotalQuantityByConcertIdAndSeatZoneId(@Param("concertId") UUID concertId, @Param("seatZoneId") UUID seatZoneId);
}
