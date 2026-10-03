package com.ticketbox.api.module.artistbio.repositories;

import com.ticketbox.api.module.artistbio.domain.entities.ArtistBioJob;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface ArtistBioJobRepository extends JpaRepository<ArtistBioJob, UUID> {
    Optional<ArtistBioJob> findByIdAndConcertId(UUID id, UUID concertId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from ArtistBioJob j where j.id = :id")
    Optional<ArtistBioJob> findForUpdate(UUID id);

    @Query("""
        select j.id from ArtistBioJob j
        where (j.status = 'PENDING' and (j.nextAttemptAt is null or j.nextAttemptAt <= :now))
           or (j.status = 'PROCESSING' and (j.leaseUntil is null or j.leaseUntil <= :now))
        order by j.updatedAt, j.id
        """)
    List<UUID> findRecoverable(LocalDateTime now, Pageable pageable);
}
