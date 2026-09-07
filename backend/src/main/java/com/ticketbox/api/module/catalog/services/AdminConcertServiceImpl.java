package com.ticketbox.api.module.catalog.services;

import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserRole;
import com.ticketbox.api.module.catalog.domain.dtos.*;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.ConcertStatus;
import com.ticketbox.api.module.catalog.domain.entities.SeatZone;
import com.ticketbox.api.module.catalog.domain.entities.TicketType;
import com.ticketbox.api.module.catalog.domain.entities.TicketTypeStatus;
import com.ticketbox.api.module.catalog.repositories.ConcertRepository;
import com.ticketbox.api.module.catalog.repositories.SeatZoneRepository;
import com.ticketbox.api.module.catalog.repositories.TicketTypeRepository;
import com.ticketbox.api.module.shared.cache.CacheService;
import com.ticketbox.api.module.shared.storage.StorageService;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminConcertServiceImpl implements AdminConcertService {

    private final ConcertRepository concertRepository;
    private final SeatZoneRepository seatZoneRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final CacheService cacheService;
    private final StorageService storageService;

    @Override
    @Transactional(readOnly = true)
    public Page<ConcertDetailResponse> getAdminConcerts(User currentUser, String statusStr, String q, Pageable pageable) {
        Specification<Concert> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Role ownership check: ORGANIZER can only see their own concerts
            if (currentUser.getRole() == UserRole.ORGANIZER) {
                predicates.add(cb.equal(root.get("organizer").get("id"), currentUser.getId()));
            }

            if (statusStr != null && !statusStr.trim().isEmpty()) {
                try {
                    ConcertStatus statusEnum = ConcertStatus.valueOf(statusStr.toUpperCase());
                    predicates.add(cb.equal(root.get("status"), statusEnum));
                } catch (IllegalArgumentException e) {
                    throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Invalid concert status: " + statusStr);
                }
            }

            if (q != null && !q.trim().isEmpty()) {
                String pattern = "%" + q.trim().toLowerCase() + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
                Predicate artistMatch = cb.like(cb.lower(root.get("artistName")), pattern);
                predicates.add(cb.or(titleMatch, artistMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Concert> concertPage = concertRepository.findAll(spec, pageable);
        return concertPage.map(this::mapToConcertDetailResponse);
    }

    @Override
    public ConcertDetailResponse createConcert(User currentUser, CreateConcertRequest request) {
        if (concertRepository.existsBySlug(request.getSlug())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "SLUG_ALREADY_EXISTS", "Concert slug already exists: " + request.getSlug());
        }

        if (request.getEndsAt() != null && request.getStartsAt() != null && !request.getEndsAt().isAfter(request.getStartsAt())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_DATES", "Ends at time must be strictly after starts at time");
        }

        Concert concert = Concert.builder()
                .title(request.getTitle())
                .slug(request.getSlug())
                .venue(request.getVenue())
                .description(request.getDescription())
                .artistName(request.getArtistName())
                .artistBio(request.getArtistBio())
                .startsAt(request.getStartsAt())
                .endsAt(request.getEndsAt())
                .coverImageUrl(request.getCoverImageUrl())
                .seatMapUrl(request.getSeatMapUrl())
                .organizer(currentUser)
                .status(ConcertStatus.DRAFT)
                .build();

        Concert savedConcert = concertRepository.save(concert);
        log.info("Created concert {} (ID: {}) by user {}", savedConcert.getTitle(), savedConcert.getId(), currentUser.getEmail());
        cacheService.evictAllConcertsCache();
        return mapToConcertDetailResponse(savedConcert);
    }

    @Override
    public ConcertDetailResponse updateConcert(User currentUser, UUID concertId, UpdateConcertRequest request) {
        Concert concert = getConcertAndCheckOwnership(currentUser, concertId);

        if (request.getTitle() != null && !request.getTitle().trim().isEmpty()) {
            concert.setTitle(request.getTitle());
        }
        if (request.getVenue() != null && !request.getVenue().trim().isEmpty()) {
            concert.setVenue(request.getVenue());
        }
        if (request.getDescription() != null) {
            concert.setDescription(request.getDescription());
        }
        if (request.getArtistName() != null && !request.getArtistName().trim().isEmpty()) {
            concert.setArtistName(request.getArtistName());
        }
        if (request.getArtistBio() != null) {
            concert.setArtistBio(request.getArtistBio());
        }
        if (request.getCoverImageUrl() != null) {
            concert.setCoverImageUrl(request.getCoverImageUrl());
        }
        if (request.getSeatMapUrl() != null) {
            concert.setSeatMapUrl(request.getSeatMapUrl());
        }

        if (request.getStartsAt() != null) {
            concert.setStartsAt(request.getStartsAt());
        }
        if (request.getEndsAt() != null) {
            concert.setEndsAt(request.getEndsAt());
        }

        if (!concert.getEndsAt().isAfter(concert.getStartsAt())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_DATES", "Concert end date must be strictly after start date");
        }

        Concert updatedConcert = concertRepository.save(concert);
        cacheService.evictConcertCache(concertId);
        cacheService.evictAllConcertsCache();
        return mapToConcertDetailResponse(updatedConcert);
    }

    @Override
    public ConcertDetailResponse publishConcert(User currentUser, UUID concertId) {
        Concert concert = getConcertAndCheckOwnership(currentUser, concertId);

        List<SeatZone> seatZones = seatZoneRepository.findByConcertIdOrderBySortOrderAsc(concertId);
        if (seatZones.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "PUBLISH_FAILED", "Cannot publish concert without at least one seat zone");
        }

        List<TicketType> ticketTypes = ticketTypeRepository.findByConcertId(concertId);
        if (ticketTypes.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "PUBLISH_FAILED", "Cannot publish concert without at least one ticket type");
        }

        // Validate zone capacity vs total ticket types quantity per zone
        for (SeatZone zone : seatZones) {
            Integer totalAllocated = ticketTypeRepository.sumTotalQuantityByConcertIdAndSeatZoneId(concertId, zone.getId());
            if (totalAllocated != null && totalAllocated > zone.getCapacity()) {
                throw new AppException(HttpStatus.BAD_REQUEST, "PUBLISH_FAILED",
                        String.format("Zone '%s' (capacity %d) has %d total ticket types quantity allocated, exceeding capacity",
                                zone.getName(), zone.getCapacity(), totalAllocated));
            }
        }

        // Check ticket type sale windows
        for (TicketType tt : ticketTypes) {
            if (!tt.getSaleEndAt().isAfter(tt.getSaleStartAt())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "PUBLISH_FAILED",
                        "Ticket type '" + tt.getName() + "' has invalid sale window (saleEndAt must be after saleStartAt)");
            }
            if (tt.getStatus() == TicketTypeStatus.DRAFT) {
                tt.setStatus(TicketTypeStatus.ON_SALE);
                ticketTypeRepository.save(tt);
            }
        }

        concert.setStatus(ConcertStatus.PUBLISHED);
        Concert publishedConcert = concertRepository.save(concert);
        log.info("Published concert {} by user {}", publishedConcert.getId(), currentUser.getEmail());
        cacheService.evictConcertCache(concertId);
        cacheService.evictAllConcertsCache();
        return mapToConcertDetailResponse(publishedConcert);
    }

    @Override
    public ConcertDetailResponse cancelConcert(User currentUser, UUID concertId, String reason) {
        Concert concert = getConcertAndCheckOwnership(currentUser, concertId);

        concert.setStatus(ConcertStatus.CANCELED);
        Concert cancelledConcert = concertRepository.save(concert);
        log.info("Cancelled concert {} by user {}. Reason: {}", cancelledConcert.getId(), currentUser.getEmail(), reason);
        cacheService.evictConcertCache(concertId);
        cacheService.evictAllConcertsCache();
        return mapToConcertDetailResponse(cancelledConcert);
    }

    @Override
    public SeatZoneResponse createSeatZone(User currentUser, UUID concertId, CreateSeatZoneRequest request) {
        Concert concert = getConcertAndCheckOwnership(currentUser, concertId);

        if (seatZoneRepository.existsByConcertIdAndCode(concertId, request.getCode())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "ZONE_CODE_EXISTS",
                    "Seat zone code '" + request.getCode() + "' already exists for this concert");
        }

        SeatZone zone = SeatZone.builder()
                .concert(concert)
                .code(request.getCode().toUpperCase())
                .name(request.getName())
                .description(request.getDescription())
                .capacity(request.getCapacity())
                .svgPath(request.getSvgPath())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();

        SeatZone savedZone = seatZoneRepository.save(zone);
        cacheService.evictConcertCache(concertId);
        return mapToSeatZoneResponse(savedZone);
    }

    @Override
    public SeatZoneResponse updateSeatZone(User currentUser, UUID seatZoneId, UpdateSeatZoneRequest request) {
        SeatZone zone = seatZoneRepository.findById(seatZoneId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SEAT_ZONE_NOT_FOUND", "Seat zone not found with ID: " + seatZoneId));

        checkConcertOwnership(currentUser, zone.getConcert());

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            zone.setName(request.getName());
        }
        if (request.getDescription() != null) {
            zone.setDescription(request.getDescription());
        }
        if (request.getCapacity() != null) {
            zone.setCapacity(request.getCapacity());
        }
        if (request.getSvgPath() != null) {
            zone.setSvgPath(request.getSvgPath());
        }
        if (request.getSortOrder() != null) {
            zone.setSortOrder(request.getSortOrder());
        }

        SeatZone updatedZone = seatZoneRepository.save(zone);
        cacheService.evictConcertCache(zone.getConcert().getId());
        return mapToSeatZoneResponse(updatedZone);
    }

    @Override
    public TicketTypeResponse createTicketType(User currentUser, UUID concertId, CreateTicketTypeRequest request) {
        Concert concert = getConcertAndCheckOwnership(currentUser, concertId);

        SeatZone seatZone = seatZoneRepository.findByIdAndConcertId(request.getSeatZoneId(), concertId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SEAT_ZONE_NOT_FOUND",
                        "Seat zone not found with ID " + request.getSeatZoneId() + " for this concert"));

        if (ticketTypeRepository.existsByConcertIdAndName(concertId, request.getName())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "TICKET_TYPE_EXISTS",
                    "Ticket type with name '" + request.getName() + "' already exists for this concert");
        }

        if (!request.getSaleEndAt().isAfter(request.getSaleStartAt())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_SALE_WINDOW", "Sale end time must be strictly after sale start time");
        }

        TicketType ticketType = TicketType.builder()
                .concert(concert)
                .seatZone(seatZone)
                .name(request.getName())
                .description(request.getDescription())
                .price(request.getPrice())
                .currency(request.getCurrency() != null ? request.getCurrency() : "VND")
                .totalQuantity(request.getTotalQuantity())
                .heldQuantity(0)
                .soldQuantity(0)
                .maxPerUser(request.getMaxPerUser())
                .saleStartAt(request.getSaleStartAt())
                .saleEndAt(request.getSaleEndAt())
                .status(TicketTypeStatus.DRAFT)
                .build();

        TicketType savedTicketType = ticketTypeRepository.save(ticketType);
        cacheService.evictConcertCache(concertId);
        return mapToTicketTypeResponse(savedTicketType);
    }

    @Override
    public TicketTypeResponse updateTicketType(User currentUser, UUID ticketTypeId, UpdateTicketTypeRequest request) {
        TicketType ticketType = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "TICKET_TYPE_NOT_FOUND", "Ticket type not found with ID: " + ticketTypeId));

        checkConcertOwnership(currentUser, ticketType.getConcert());

        if (request.getName() != null && !request.getName().trim().isEmpty()) {
            ticketType.setName(request.getName());
        }
        if (request.getDescription() != null) {
            ticketType.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            ticketType.setPrice(request.getPrice());
        }
        if (request.getTotalQuantity() != null) {
            if (request.getTotalQuantity() < (ticketType.getHeldQuantity() + ticketType.getSoldQuantity())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY",
                        "Total quantity cannot be set lower than the sum of held (" + ticketType.getHeldQuantity() + ") and sold (" + ticketType.getSoldQuantity() + ")");
            }
            ticketType.setTotalQuantity(request.getTotalQuantity());
        }
        if (request.getMaxPerUser() != null) {
            ticketType.setMaxPerUser(request.getMaxPerUser());
        }
        if (request.getSaleStartAt() != null) {
            ticketType.setSaleStartAt(request.getSaleStartAt());
        }
        if (request.getSaleEndAt() != null) {
            ticketType.setSaleEndAt(request.getSaleEndAt());
        }
        if (!ticketType.getSaleEndAt().isAfter(ticketType.getSaleStartAt())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_SALE_WINDOW", "Sale end time must be strictly after sale start time");
        }
        if (request.getStatus() != null) {
            try {
                TicketTypeStatus statusEnum = TicketTypeStatus.valueOf(request.getStatus().toUpperCase());
                ticketType.setStatus(statusEnum);
            } catch (IllegalArgumentException e) {
                throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Invalid ticket type status: " + request.getStatus());
            }
        }

        TicketType updatedTicketType = ticketTypeRepository.save(ticketType);
        cacheService.evictConcertCache(ticketType.getConcert().getId());
        return mapToTicketTypeResponse(updatedTicketType);
    }

    private Concert getConcertAndCheckOwnership(User currentUser, UUID concertId) {
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "CONCERT_NOT_FOUND", "Concert not found with ID: " + concertId));
        checkConcertOwnership(currentUser, concert);
        return concert;
    }

    private void checkConcertOwnership(User currentUser, Concert concert) {
        if (currentUser.getRole() == UserRole.ADMIN) {
            return; // Admin has full access
        }
        if (concert.getOrganizer() == null || !concert.getOrganizer().getId().equals(currentUser.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to access or modify this concert");
        }
    }

    private ConcertDetailResponse mapToConcertDetailResponse(Concert concert) {
        return ConcertDetailResponse.builder()
                .id(concert.getId())
                .title(concert.getTitle())
                .slug(concert.getSlug())
                .venue(concert.getVenue())
                .description(concert.getDescription())
                .artistName(concert.getArtistName())
                .artistBio(concert.getArtistBio())
                .startsAt(concert.getStartsAt())
                .endsAt(concert.getEndsAt())
                .status(concert.getStatus().name())
                .coverImageUrl(storageService.buildPublicUrl(concert.getCoverImageUrl()))
                .seatMapUrl(storageService.buildPublicUrl(concert.getSeatMapUrl()))
                .organizerId(concert.getOrganizer() != null ? concert.getOrganizer().getId() : null)
                .organizerName(concert.getOrganizer() != null ? concert.getOrganizer().getFullName() : null)
                .createdAt(concert.getCreatedAt())
                .updatedAt(concert.getUpdatedAt())
                .build();
    }

    private SeatZoneResponse mapToSeatZoneResponse(SeatZone zone) {
        return SeatZoneResponse.builder()
                .id(zone.getId())
                .concertId(zone.getConcert().getId())
                .code(zone.getCode())
                .name(zone.getName())
                .description(zone.getDescription())
                .capacity(zone.getCapacity())
                .svgPath(zone.getSvgPath())
                .sortOrder(zone.getSortOrder())
                .build();
    }

    private TicketTypeResponse mapToTicketTypeResponse(TicketType tt) {
        return TicketTypeResponse.builder()
                .id(tt.getId())
                .concertId(tt.getConcert().getId())
                .seatZoneId(tt.getSeatZone().getId())
                .zoneCode(tt.getSeatZone().getCode())
                .name(tt.getName())
                .description(tt.getDescription())
                .price(tt.getPrice())
                .currency(tt.getCurrency())
                .totalQuantity(tt.getTotalQuantity())
                .heldQuantity(tt.getHeldQuantity())
                .soldQuantity(tt.getSoldQuantity())
                .availableQuantity(tt.getAvailableQuantity())
                .maxPerUser(tt.getMaxPerUser())
                .saleStartAt(tt.getSaleStartAt())
                .saleEndAt(tt.getSaleEndAt())
                .status(tt.getStatus().name())
                .build();
    }
}
