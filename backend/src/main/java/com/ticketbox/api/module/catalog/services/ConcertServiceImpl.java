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
import com.ticketbox.api.module.audit.services.AuditLogService;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.Map;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DataIntegrityViolationException;
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
public class ConcertServiceImpl implements ConcertService {

    private final ConcertRepository concertRepository;
    private final SeatZoneRepository seatZoneRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final CacheService cacheService;
    private final StorageService storageService;
    private final AuditLogService auditLogService;

    @Override
    @Transactional(readOnly = true)
    public Page<AdminConcertResponse> getConcerts(User currentUser, String statusStr, String q, Pageable pageable) {
        Specification<Concert> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Role ownership check: ORGANIZER can only see their own concerts
            if (currentUser.getRole() == UserRole.ORGANIZER) {
                predicates.add(cb.equal(root.get("organizer").get("id"), currentUser.getId()));
            }

            if (statusStr != null && !statusStr.trim().isEmpty()) {
                String status = statusStr.trim().toUpperCase();
                if (status.equals("CANCELED") || status.equals("CANCELLED")) {
                    predicates.add(root.get("status").in(ConcertStatus.CANCELED, ConcertStatus.CANCELLED));
                } else {
                    try {
                        predicates.add(cb.equal(root.get("status"), ConcertStatus.valueOf(status)));
                    } catch (IllegalArgumentException e) {
                        throw new AppException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Invalid concert status: " + statusStr);
                    }
                }
            }

            if (q != null && !q.trim().isEmpty()) {
                String pattern = "%" + escapeLike(q.trim().toLowerCase()) + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern, '\\');
                Predicate artistMatch = cb.like(cb.lower(root.get("artistName")), pattern, '\\');
                predicates.add(cb.or(titleMatch, artistMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Concert> concertPage = concertRepository.findAll(spec, pageable);
        return concertPage.map(this::mapToConcertDetailResponse);
    }

    @Override
    public AdminConcertResponse createConcert(User currentUser, CreateConcertRequest request) {
        if (request.getSlug() == null || request.getSlug().isBlank() || request.getTitle() == null || request.getTitle().isBlank()
                || request.getVenue() == null || request.getVenue().isBlank() || request.getArtistName() == null || request.getArtistName().isBlank()
                || request.getSlug().length() > 255 || request.getTitle().length() > 255 || request.getVenue().length() > 255 || request.getArtistName().length() > 255
                || request.getStartsAt() == null || request.getEndsAt() == null) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Required concert fields are invalid");
        }
        if (concertRepository.existsBySlug(request.getSlug())) {
            throw new AppException(HttpStatus.CONFLICT, "SLUG_ALREADY_EXISTS", "Concert slug already exists: " + request.getSlug());
        }

        if (request.getEndsAt() != null && request.getStartsAt() != null && !request.getEndsAt().isAfter(request.getStartsAt())) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CONCERT_TIME_RANGE", "Ends at time must be strictly after starts at time");
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

        Concert savedConcert;
        try {
            savedConcert = concertRepository.saveAndFlush(concert);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMessage() != null && exception.getMessage().toLowerCase().contains("slug")) {
                throw new AppException(HttpStatus.CONFLICT, "SLUG_ALREADY_EXISTS", "Concert slug already exists");
            }
            throw exception;
        }
        log.info("Created concert {} (ID: {}) by user {}", savedConcert.getTitle(), savedConcert.getId(), currentUser.getEmail());
        audit(currentUser, "CREATE_CONCERT", "CONCERT", savedConcert.getId(), null, savedConcert);
        invalidateAfterCommit(savedConcert.getId());
        return mapToConcertDetailResponse(savedConcert);
    }

    @Override
    public AdminConcertResponse updateConcert(User currentUser, UUID concertId, UpdateConcertRequest request) {
        Concert concert = lockConcertAndCheckOwnership(currentUser, concertId);
        checkEditable(concert);
        Concert before = copyConcert(concert);

        if (request.wasSupplied("title") || request.getTitle() != null) concert.setTitle(requireText(request.getTitle(), "title", 255));
        if (request.wasSupplied("venue") || request.getVenue() != null) concert.setVenue(requireText(request.getVenue(), "venue", 255));
        if (request.wasSupplied("description") || request.getDescription() != null) {
            concert.setDescription(request.getDescription());
        }
        if (request.wasSupplied("artist_name") || request.getArtistName() != null) concert.setArtistName(requireText(request.getArtistName(), "artist_name", 255));
        if (request.wasSupplied("artist_bio") || request.getArtistBio() != null) {
            concert.setArtistBio(request.getArtistBio());
        }
        if (request.wasSupplied("cover_image_url") || request.getCoverImageUrl() != null) {
            concert.setCoverImageUrl(request.getCoverImageUrl());
        }
        if (request.wasSupplied("seat_map_url") || request.getSeatMapUrl() != null) {
            concert.setSeatMapUrl(request.getSeatMapUrl());
        }

        if (request.wasSupplied("starts_at") || request.getStartsAt() != null) {
            if (request.getStartsAt() == null) throw validation("starts_at cannot be null");
            concert.setStartsAt(request.getStartsAt());
        }
        if (request.wasSupplied("ends_at") || request.getEndsAt() != null) {
            if (request.getEndsAt() == null) throw validation("ends_at cannot be null");
            concert.setEndsAt(request.getEndsAt());
        }
        if (!concert.getEndsAt().isAfter(concert.getStartsAt())) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CONCERT_TIME_RANGE", "Concert end date must be strictly after start date");
        }

        if (sameConcert(before, concert)) return mapToConcertDetailResponse(concert);
        Concert updatedConcert = concertRepository.save(concert);
        audit(currentUser, "UPDATE_CONCERT", "CONCERT", concertId, before, updatedConcert);
        invalidateAfterCommit(concertId);
        return mapToConcertDetailResponse(updatedConcert);
    }

    @Override
    public AdminConcertResponse publishConcert(User currentUser, UUID concertId) {
        Concert concert = lockConcertAndCheckOwnership(currentUser, concertId);
        if (concert.getStatus() == ConcertStatus.PUBLISHED) return mapToConcertDetailResponse(concert);
        if (concert.getStatus() != ConcertStatus.DRAFT) throw invalidState();

        List<SeatZone> seatZones = seatZoneRepository.findByConcertIdOrderBySortOrderAsc(concertId);
        if (seatZones.isEmpty()) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "CANNOT_PUBLISH_CONCERT", "Concert requires at least one seat zone");
        }

        List<TicketType> ticketTypes = ticketTypeRepository.findByConcertId(concertId);
        if (ticketTypes.isEmpty()) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "CANNOT_PUBLISH_CONCERT", "Concert requires at least one ticket type");
        }

        // Validate zone capacity vs total ticket types quantity per zone
        for (SeatZone zone : seatZones) {
            Long totalAllocated = ticketTypeRepository.sumTotalQuantityByConcertIdAndSeatZoneId(concertId, zone.getId());
            if (totalAllocated != null && totalAllocated > zone.getCapacity()) {
                throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "CANNOT_PUBLISH_CONCERT",
                        String.format("Zone '%s' (capacity %d) has %d total ticket types quantity allocated, exceeding capacity",
                                zone.getName(), zone.getCapacity(), totalAllocated));
            }
        }

        // Check ticket type sale windows
        for (TicketType tt : ticketTypes) {
            if (!tt.getSaleEndAt().isAfter(tt.getSaleStartAt())) {
                throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "CANNOT_PUBLISH_CONCERT",
                        "Ticket type '" + tt.getName() + "' has invalid sale window (saleEndAt must be after saleStartAt)");
            }
            if (tt.getStatus() == TicketTypeStatus.DRAFT) {
                tt.setStatus(TicketTypeStatus.ON_SALE);
                ticketTypeRepository.save(tt);
            }
        }

        Concert before = copyConcert(concert);
        concert.setStatus(ConcertStatus.PUBLISHED);
        Concert publishedConcert = concertRepository.save(concert);
        log.info("Published concert {} by user {}", publishedConcert.getId(), currentUser.getEmail());
        audit(currentUser, "PUBLISH_CONCERT", "CONCERT", concertId, before, publishedConcert);
        invalidateAfterCommit(concertId);
        return mapToConcertDetailResponse(publishedConcert);
    }

    @Override
    public AdminConcertResponse cancelConcert(User currentUser, UUID concertId, String reason) {
        Concert concert = lockConcertAndCheckOwnership(currentUser, concertId);
        if (concert.getStatus() == ConcertStatus.CANCELED || concert.getStatus() == ConcertStatus.CANCELLED) return mapToConcertDetailResponse(concert);
        if (concert.getStatus() != ConcertStatus.DRAFT && concert.getStatus() != ConcertStatus.PUBLISHED) throw invalidState();

        Concert before = copyConcert(concert);
        concert.setStatus(ConcertStatus.CANCELED);
        Concert cancelledConcert = concertRepository.save(concert);
        log.info("Cancelled concert {} by user {}. Reason: {}", cancelledConcert.getId(), currentUser.getEmail(), reason);
        audit(currentUser, "CANCEL_CONCERT", "CONCERT", concertId, before, Map.of("after", cancelledConcert, "reason", reason == null ? "" : reason));
        invalidateAfterCommit(concertId);
        return mapToConcertDetailResponse(cancelledConcert);
    }

    @Override
    public AdminSeatZoneResponse createSeatZone(User currentUser, UUID concertId, CreateSeatZoneRequest request) {
        Concert concert = lockConcertAndCheckOwnership(currentUser, concertId);
        checkEditable(concert);
        if (request.getCode() == null || request.getCode().isBlank() || request.getCode().length() > 50
                || request.getName() == null || request.getName().isBlank() || request.getName().length() > 100
                || request.getCapacity() == null || request.getCapacity() <= 0) {
            throw validation("Invalid seat zone fields");
        }
        String code = request.getCode().trim().toUpperCase(java.util.Locale.ROOT);

        if (seatZoneRepository.existsByConcertIdAndCode(concertId, code)) {
            throw new AppException(HttpStatus.CONFLICT, "SEAT_ZONE_CODE_ALREADY_EXISTS",
                    "Seat zone code already exists for this concert");
        }

        SeatZone zone = SeatZone.builder()
                .concert(concert)
                .code(code)
                .name(request.getName())
                .description(request.getDescription())
                .capacity(request.getCapacity())
                .svgPath(request.getSvgPath())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();

        SeatZone savedZone;
        try {
            savedZone = seatZoneRepository.saveAndFlush(zone);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMessage() != null && exception.getMessage().toLowerCase().contains("code")) {
                throw new AppException(HttpStatus.CONFLICT, "SEAT_ZONE_CODE_ALREADY_EXISTS", "Seat zone code already exists");
            }
            throw exception;
        }
        audit(currentUser, "CREATE_SEAT_ZONE", "SEAT_ZONE", savedZone.getId(), null, savedZoneSnapshot(savedZone));
        invalidateAfterCommit(concertId);
        return mapToSeatZoneResponse(savedZone);
    }

    @Override
    public AdminSeatZoneResponse updateSeatZone(User currentUser, UUID seatZoneId, UpdateSeatZoneRequest request) {
        SeatZone initial = seatZoneRepository.findById(seatZoneId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SEAT_ZONE_NOT_FOUND", "Seat zone not found with ID: " + seatZoneId));
        Concert concert = lockConcertAndCheckOwnership(currentUser, initial.getConcert().getId());
        checkEditable(concert);
        SeatZone zone = seatZoneRepository.findById(seatZoneId).orElseThrow(
                () -> new AppException(HttpStatus.NOT_FOUND, "SEAT_ZONE_NOT_FOUND", "Seat zone not found"));
        Map<String, Object> before = savedZoneSnapshot(zone);

        if (request.wasSupplied("name") || request.getName() != null) zone.setName(requireText(request.getName(), "name", 100));
        if (request.wasSupplied("description") || request.getDescription() != null) {
            zone.setDescription(request.getDescription());
        }
        if (request.wasSupplied("capacity") || request.getCapacity() != null) {
            if (request.getCapacity() == null) throw validation("capacity cannot be null");
            zone.setCapacity(request.getCapacity());
        }
        if (request.wasSupplied("svg_path") || request.getSvgPath() != null) {
            zone.setSvgPath(request.getSvgPath());
        }
        if (request.wasSupplied("sort_order") || request.getSortOrder() != null) {
            if (request.getSortOrder() == null) throw validation("sort_order cannot be null");
            zone.setSortOrder(request.getSortOrder());
        }

        Long allocated = ticketTypeRepository.sumTotalQuantityByConcertIdAndSeatZoneId(concert.getId(), zone.getId());
        if (zone.getCapacity() == null || zone.getCapacity() <= 0 || allocated != null && allocated > zone.getCapacity()) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "ZONE_CAPACITY_EXCEEDED", "Capacity is below configured ticket quantities");
        }
        if (java.util.Objects.equals(before, savedZoneSnapshot(zone))) return mapToSeatZoneResponse(zone);
        SeatZone updatedZone = seatZoneRepository.save(zone);
        audit(currentUser, "UPDATE_SEAT_ZONE", "SEAT_ZONE", zone.getId(), before, savedZoneSnapshot(zone));
        invalidateAfterCommit(concert.getId());
        return mapToSeatZoneResponse(updatedZone);
    }

    @Override
    public AdminTicketTypeResponse createTicketType(User currentUser, UUID concertId, CreateTicketTypeRequest request) {
        Concert concert = lockConcertAndCheckOwnership(currentUser, concertId);
        checkEditable(concert);

        SeatZone seatZone = seatZoneRepository.findByIdAndConcertId(request.getSeatZoneId(), concertId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "SEAT_ZONE_NOT_FOUND",
                        "Seat zone not found with ID " + request.getSeatZoneId() + " for this concert"));

        if (request.getName() == null || request.getName().isBlank() || request.getName().length() > 100) throw validation("Invalid ticket type name");
        if (ticketTypeRepository.existsByConcertIdAndName(concertId, request.getName())) {
            throw new AppException(HttpStatus.CONFLICT, "TICKET_TYPE_NAME_ALREADY_EXISTS",
                    "Ticket type with name '" + request.getName() + "' already exists for this concert");
        }

        if (!request.getSaleEndAt().isAfter(request.getSaleStartAt())) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SALE_WINDOW", "Sale end time must be strictly after sale start time");
        }
        validatePriceAndQuantity(request.getPrice(), request.getTotalQuantity(), request.getMaxPerUser());
        Long allocated = ticketTypeRepository.sumTotalQuantityByConcertIdAndSeatZoneId(concertId, seatZone.getId());
        if (allocated != null && allocated + request.getTotalQuantity() > seatZone.getCapacity()) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "ZONE_CAPACITY_EXCEEDED", "Ticket quantities exceed zone capacity");
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

        TicketType savedTicketType;
        try {
            savedTicketType = ticketTypeRepository.saveAndFlush(ticketType);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMessage() != null && exception.getMessage().toLowerCase().contains("name")) {
                throw new AppException(HttpStatus.CONFLICT, "TICKET_TYPE_NAME_ALREADY_EXISTS", "Ticket type name already exists");
            }
            throw exception;
        }
        audit(currentUser, "CREATE_TICKET_TYPE", "TICKET_TYPE", savedTicketType.getId(), null, ticketSnapshot(savedTicketType));
        invalidateAfterCommit(concertId);
        return mapToTicketTypeResponse(savedTicketType);
    }

    @Override
    public AdminTicketTypeResponse updateTicketType(User currentUser, UUID ticketTypeId, UpdateTicketTypeRequest request) {
        TicketType initial = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "TICKET_TYPE_NOT_FOUND", "Ticket type not found with ID: " + ticketTypeId));
        Concert concert = lockConcertAndCheckOwnership(currentUser, initial.getConcert().getId());
        checkEditable(concert);
        TicketType ticketType = ticketTypeRepository.findByIdForUpdate(ticketTypeId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "TICKET_TYPE_NOT_FOUND", "Ticket type not found with ID: " + ticketTypeId));
        Map<String, Object> before = ticketSnapshot(ticketType);

        if (request.wasSupplied("name") || request.getName() != null) ticketType.setName(requireText(request.getName(), "name", 100));
        if (!ticketType.getName().equals(before.get("name"))
                && ticketTypeRepository.existsByConcertIdAndNameAndIdNot(concert.getId(), ticketType.getName(), ticketTypeId)) {
            throw new AppException(HttpStatus.CONFLICT, "TICKET_TYPE_NAME_ALREADY_EXISTS", "Ticket type name already exists for this concert");
        }
        if (request.wasSupplied("description") || request.getDescription() != null) {
            ticketType.setDescription(request.getDescription());
        }
        if (request.wasSupplied("price") || request.getPrice() != null) {
            if (request.getPrice() == null) throw validation("price cannot be null");
            ticketType.setPrice(request.getPrice());
        }
        if (request.wasSupplied("total_quantity") || request.getTotalQuantity() != null) {
            if (request.getTotalQuantity() == null) throw validation("total_quantity cannot be null");
            if (request.getTotalQuantity() < (ticketType.getHeldQuantity() + ticketType.getSoldQuantity())) {
                throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_QUANTITY",
                        "Total quantity cannot be set lower than the sum of held (" + ticketType.getHeldQuantity() + ") and sold (" + ticketType.getSoldQuantity() + ")");
            }
            ticketType.setTotalQuantity(request.getTotalQuantity());
        }
        if (request.getPrice() != null) validatePrice(request.getPrice());
        if (request.wasSupplied("max_per_user") || request.getMaxPerUser() != null) {
            if (request.getMaxPerUser() == null) throw validation("max_per_user cannot be null");
            ticketType.setMaxPerUser(request.getMaxPerUser());
        }
        if (request.wasSupplied("sale_start_at") || request.getSaleStartAt() != null) {
            if (request.getSaleStartAt() == null) throw validation("sale_start_at cannot be null");
            ticketType.setSaleStartAt(request.getSaleStartAt());
        }
        if (request.wasSupplied("sale_end_at") || request.getSaleEndAt() != null) {
            if (request.getSaleEndAt() == null) throw validation("sale_end_at cannot be null");
            ticketType.setSaleEndAt(request.getSaleEndAt());
        }
        if (!ticketType.getSaleEndAt().isAfter(ticketType.getSaleStartAt())) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SALE_WINDOW", "Sale end time must be strictly after sale start time");
        }
        Long allocated = ticketTypeRepository.sumTotalQuantityByConcertIdAndSeatZoneId(concert.getId(), ticketType.getSeatZone().getId());
        if (allocated != null && allocated > ticketType.getSeatZone().getCapacity()) {
            throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "ZONE_CAPACITY_EXCEEDED", "Ticket quantities exceed zone capacity");
        }
        if (request.wasSupplied("status") || request.getStatus() != null) {
            if (request.getStatus() == null) throw validation("status cannot be null");
            try {
                TicketTypeStatus statusEnum = TicketTypeStatus.valueOf(request.getStatus().toUpperCase());
                ticketType.setStatus(statusEnum);
            } catch (IllegalArgumentException e) {
                throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Invalid ticket type status: " + request.getStatus());
            }
        }

        if (java.util.Objects.equals(before, ticketSnapshot(ticketType))) return mapToTicketTypeResponse(ticketType);
        TicketType updatedTicketType;
        try {
            updatedTicketType = ticketTypeRepository.saveAndFlush(ticketType);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMessage() != null && exception.getMessage().toLowerCase().contains("name")) {
                throw new AppException(HttpStatus.CONFLICT, "TICKET_TYPE_NAME_ALREADY_EXISTS", "Ticket type name already exists");
            }
            throw exception;
        }
        audit(currentUser, "UPDATE_TICKET_TYPE", "TICKET_TYPE", ticketTypeId, before, ticketSnapshot(updatedTicketType));
        invalidateAfterCommit(concert.getId());
        return mapToTicketTypeResponse(updatedTicketType);
    }

    private Concert getConcertAndCheckOwnership(User currentUser, UUID concertId) {
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "CONCERT_NOT_FOUND", "Concert not found with ID: " + concertId));
        checkConcertOwnership(currentUser, concert);
        return concert;
    }

    private Concert lockConcertAndCheckOwnership(User currentUser, UUID concertId) {
        Concert concert = concertRepository.findByIdForUpdate(concertId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "CONCERT_NOT_FOUND", "Concert not found"));
        checkConcertOwnership(currentUser, concert);
        return concert;
    }

    private void checkEditable(Concert concert) {
        if (concert.getStatus() != ConcertStatus.DRAFT && concert.getStatus() != ConcertStatus.PUBLISHED) {
            throw invalidState();
        }
    }

    private AppException invalidState() {
        return new AppException(HttpStatus.CONFLICT, "INVALID_CONCERT_STATE", "Concert state does not allow this operation");
    }

    private AppException validation(String message) {
        return new AppException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", message);
    }

    private String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw validation(field + " must be non-blank and at most " + maxLength + " characters");
        }
        return value;
    }

    private void validatePrice(java.math.BigDecimal price) {
        if (price.signum() < 0 || price.scale() > 2 || price.precision() > 12) {
            throw validation("price must fit NUMERIC(12,2) and be non-negative");
        }
    }

    private void validatePriceAndQuantity(java.math.BigDecimal price, Integer quantity, Integer maxPerUser) {
        validatePrice(price);
        if (quantity == null || quantity < 0 || maxPerUser == null || maxPerUser < 1) throw validation("Invalid ticket quantity or max_per_user");
    }

    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private Concert copyConcert(Concert c) {
        return Concert.builder().id(c.getId()).organizer(c.getOrganizer()).title(c.getTitle()).slug(c.getSlug())
                .venue(c.getVenue()).description(c.getDescription()).artistName(c.getArtistName()).artistBio(c.getArtistBio())
                .startsAt(c.getStartsAt()).endsAt(c.getEndsAt()).status(c.getStatus()).coverImageUrl(c.getCoverImageUrl())
                .seatMapUrl(c.getSeatMapUrl()).createdAt(c.getCreatedAt()).updatedAt(c.getUpdatedAt()).build();
    }

    private boolean sameConcert(Concert a, Concert b) {
        return java.util.Objects.equals(a.getTitle(), b.getTitle()) && java.util.Objects.equals(a.getVenue(), b.getVenue())
                && java.util.Objects.equals(a.getDescription(), b.getDescription()) && java.util.Objects.equals(a.getArtistName(), b.getArtistName())
                && java.util.Objects.equals(a.getArtistBio(), b.getArtistBio()) && java.util.Objects.equals(a.getStartsAt(), b.getStartsAt())
                && java.util.Objects.equals(a.getEndsAt(), b.getEndsAt()) && java.util.Objects.equals(a.getCoverImageUrl(), b.getCoverImageUrl())
                && java.util.Objects.equals(a.getSeatMapUrl(), b.getSeatMapUrl());
    }

    private Map<String, Object> savedZoneSnapshot(SeatZone z) {
        Map<String, Object> snapshot = new java.util.HashMap<>();
        snapshot.put("id", z.getId().toString()); snapshot.put("concert_id", z.getConcert().getId().toString());
        snapshot.put("code", z.getCode()); snapshot.put("name", z.getName()); snapshot.put("description", z.getDescription());
        snapshot.put("capacity", z.getCapacity()); snapshot.put("svg_path", z.getSvgPath()); snapshot.put("sort_order", z.getSortOrder());
        return snapshot;
    }

    private Map<String, Object> ticketSnapshot(TicketType t) {
        Map<String, Object> snapshot = new java.util.HashMap<>();
        snapshot.put("id", t.getId().toString()); snapshot.put("concert_id", t.getConcert().getId().toString());
        snapshot.put("seat_zone_id", t.getSeatZone().getId().toString()); snapshot.put("name", t.getName());
        snapshot.put("description", t.getDescription()); snapshot.put("price", t.getPrice()); snapshot.put("currency", t.getCurrency());
        snapshot.put("total_quantity", t.getTotalQuantity()); snapshot.put("held_quantity", t.getHeldQuantity());
        snapshot.put("sold_quantity", t.getSoldQuantity()); snapshot.put("max_per_user", t.getMaxPerUser());
        snapshot.put("sale_start_at", t.getSaleStartAt()); snapshot.put("sale_end_at", t.getSaleEndAt());
        snapshot.put("status", t.getStatus().name());
        return snapshot;
    }

    private void audit(User actor, String action, String entity, UUID id, Object before, Object after) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        if (before != null) metadata.put("before", before instanceof Concert c ? concertSnapshot(c) : before);
        if (after != null) metadata.put("after", after instanceof Concert c ? concertSnapshot(c) : after);
        if (after instanceof Map<?, ?> map && map.containsKey("reason")) {
            metadata.put("reason", map.get("reason"));
            Object snapshot = map.get("after");
            if (snapshot instanceof Concert c) metadata.put("after", concertSnapshot(c));
        }
        auditLogService.logAction(actor, action, entity, id.toString(), metadata, null, null);
    }

    private Map<String, Object> concertSnapshot(Concert c) {
        Map<String, Object> snapshot = new java.util.HashMap<>();
        snapshot.put("id", c.getId().toString()); snapshot.put("title", c.getTitle()); snapshot.put("slug", c.getSlug());
        snapshot.put("venue", c.getVenue()); snapshot.put("description", c.getDescription()); snapshot.put("artist_name", c.getArtistName());
        snapshot.put("artist_bio", c.getArtistBio()); snapshot.put("starts_at", c.getStartsAt()); snapshot.put("ends_at", c.getEndsAt());
        snapshot.put("status", c.getStatus() == ConcertStatus.CANCELLED ? "CANCELED" : c.getStatus().name());
        snapshot.put("cover_image_url", c.getCoverImageUrl()); snapshot.put("seat_map_url", c.getSeatMapUrl());
        return snapshot;
    }

    private void invalidateAfterCommit(UUID concertId) {
        Runnable invalidation = () -> {
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    cacheService.invalidateAdminConcert(concertId);
                    return;
                } catch (RuntimeException e) {
                    if (attempt == 3) log.error("Redis cache invalidation failed for concert {} after commit", concertId, e);
                }
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { invalidation.run(); }
            });
        } else {
            invalidation.run();
        }
    }

    private void checkConcertOwnership(User currentUser, Concert concert) {
        if (currentUser.getRole() == UserRole.ADMIN) {
            return; // Admin has full access
        }
        if (concert.getOrganizer() == null || !concert.getOrganizer().getId().equals(currentUser.getId())) {
            throw new AppException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to access or modify this concert");
        }
    }

    private AdminConcertResponse mapToConcertDetailResponse(Concert concert) {
        return AdminConcertResponse.builder()
                .id(concert.getId())
                .title(concert.getTitle())
                .slug(concert.getSlug())
                .venue(concert.getVenue())
                .description(concert.getDescription())
                .artistName(concert.getArtistName())
                .artistBio(concert.getArtistBio())
                .startsAt(concert.getStartsAt().atOffset(java.time.ZoneOffset.UTC))
                .endsAt(concert.getEndsAt().atOffset(java.time.ZoneOffset.UTC))
                .status(concert.getStatus() == ConcertStatus.CANCELLED ? "CANCELED" : concert.getStatus().name())
                .coverImageUrl(storageService.buildPublicUrl(concert.getCoverImageUrl()))
                .seatMapUrl(storageService.buildPublicUrl(concert.getSeatMapUrl()))
                .organizerId(concert.getOrganizer() != null ? concert.getOrganizer().getId() : null)
                .organizerName(concert.getOrganizer() != null ? concert.getOrganizer().getFullName() : null)
                .createdAt(concert.getCreatedAt() == null ? null : concert.getCreatedAt().atOffset(java.time.ZoneOffset.UTC))
                .updatedAt(concert.getUpdatedAt() == null ? null : concert.getUpdatedAt().atOffset(java.time.ZoneOffset.UTC))
                .build();
    }

    private AdminSeatZoneResponse mapToSeatZoneResponse(SeatZone zone) {
        return AdminSeatZoneResponse.builder()
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

    private AdminTicketTypeResponse mapToTicketTypeResponse(TicketType tt) {
        return AdminTicketTypeResponse.builder()
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
                .saleStartAt(tt.getSaleStartAt().atOffset(java.time.ZoneOffset.UTC))
                .saleEndAt(tt.getSaleEndAt().atOffset(java.time.ZoneOffset.UTC))
                .status(tt.getStatus().name())
                .build();
    }
}
