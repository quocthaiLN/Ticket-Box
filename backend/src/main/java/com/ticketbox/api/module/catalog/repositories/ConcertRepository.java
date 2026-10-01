package com.ticketbox.api.module.catalog.repositories;

import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.ConcertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConcertRepository extends JpaRepository<Concert, UUID>, JpaSpecificationExecutor<Concert> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Concert c WHERE c.id = :id")
    Optional<Concert> findByIdForUpdate(@Param("id") UUID id);

    Optional<Concert> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Concert> findByOrganizerId(UUID organizerId, Pageable pageable);

    Optional<Concert> findByIdAndOrganizerId(UUID id, UUID organizerId);

    List<Concert> findByStatusAndStartsAtBetween(ConcertStatus status, LocalDateTime start, LocalDateTime end);
}
