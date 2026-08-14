package com.ticketbox.api.module.catalog.repositories;

import com.ticketbox.api.module.catalog.domain.entities.SeatZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SeatZoneRepository extends JpaRepository<SeatZone, UUID> {

    List<SeatZone> findByConcertIdOrderBySortOrderAsc(UUID concertId);

    Optional<SeatZone> findByConcertIdAndCode(UUID concertId, String code);

    boolean existsByConcertIdAndCode(UUID concertId, String code);

    Optional<SeatZone> findByIdAndConcertId(UUID id, UUID concertId);
}
