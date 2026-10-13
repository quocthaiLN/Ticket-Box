package com.ticketbox.api.module.catalog.services;

import com.ticketbox.api.module.shared.validation.RequestValidationException;
import com.ticketbox.api.module.catalog.domain.exception.*;
import com.ticketbox.api.module.catalog.domain.mappers.ConcertMapper;
import com.ticketbox.api.module.catalog.domain.mappers.SeatZoneMapper;
import com.ticketbox.api.module.catalog.domain.mappers.TicketTypeMapper;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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
    private final ConcertMapper concertMapper;
    private final SeatZoneMapper seatZoneMapper;
    private final TicketTypeMapper ticketTypeMapper;

    @Override
    @Transactional(readOnly = true)
    public Concert getConcertForArtistBioUpload(User currentUser, UUID concertId) {
        Concert concert = getConcertAndCheckOwnership(currentUser, concertId);
        checkEditable(concert);
        return concert;
    }

    @Override
    public Concert getConcertForArtistBioUploadForUpdate(User currentUser, UUID concertId) {
        Concert concert = lockConcertAndCheckOwnership(currentUser, concertId);
        checkEditable(concert);
        return concert;
    }

    @Override
    @Transactional(readOnly = true)
    public void checkArtistBioAccess(User currentUser, UUID concertId) {
        getConcertAndCheckOwnership(currentUser, concertId);
    }

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
                if (status.equals("CANCELLED")) {
                    predicates.add(root.get("status").in(ConcertStatus.CANCELLED));
                } else {
                    try {
                        predicates.add(cb.equal(root.get("status"), ConcertStatus.valueOf(status)));
                    } catch (IllegalArgumentException e) {
                        throw new RequestValidationException("INVALID_STATUS", "Invalid concert status: " + statusStr);
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
        return concertPage.map(concertMapper::toAdminResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminConcertMetadataResponse getConcertMetadata(User currentUser, UUID concertId) {
        Concert concert = getConcertAndCheckOwnership(currentUser, concertId);
        return new AdminConcertMetadataResponse(
                concertMapper.toAdminResponse(concert),
                seatZoneRepository.findByConcertIdOrderBySortOrderAsc(concertId).stream()
                        .map(seatZoneMapper::toAdminResponse).toList(),
                ticketTypeRepository.findByConcertId(concertId).stream()
                        .map(ticketTypeMapper::toAdminResponse).toList(),
                ConcertMetadataResponse.SeatMapInfo.builder()
                        .svgUrl(storageService.buildPublicUrl(concert.getSeatMapUrl()))
                        .fallbackImageUrl(storageService.buildPublicUrl(concert.getCoverImageUrl()))
                        .build(),
                concert.getArtistBio());
    }

    @Override
    public AdminConcertResponse createConcert(User currentUser, CreateConcertRequest request) {
        if (concertRepository.existsBySlug(request.getSlug())) {
            throw new ConcertSlugAlreadyExistsException(request.getSlug());
        }

        if (request.getEndsAt() != null && request.getStartsAt() != null && !request.getEndsAt().isAfter(request.getStartsAt())) {
            throw new InvalidConcertTimeRangeException();
        }

        Concert concert = concertMapper.toEntity(request);
        concert.setOrganizer(currentUser);
        concert.setStatus(ConcertStatus.DRAFT);

        Concert savedConcert;
        try {
            savedConcert = concertRepository.saveAndFlush(concert);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMessage() != null && exception.getMessage().toLowerCase().contains("slug")) {
                throw new ConcertSlugAlreadyExistsException(request.getSlug());
            }
            throw exception;
        }
        log.info("Created concert {} (ID: {}) by user {}", savedConcert.getTitle(), savedConcert.getId(), currentUser.getEmail());
        audit(currentUser, "CREATE_CONCERT", "CONCERT", savedConcert.getId(), null, savedConcert);
        invalidateAfterCommit(savedConcert.getId());
        return concertMapper.toAdminResponse(savedConcert);
    }

    @Override
    public AdminConcertResponse updateConcert(User currentUser, UUID concertId, UpdateConcertRequest request) {
        Concert concert = lockConcertAndCheckOwnership(currentUser, concertId);
        checkEditable(concert);
        Concert before = copyConcert(concert);

        if (request.getTitle() != null && request.getTitle().isPresent()) {
            concert.setTitle(requireText(request.getTitle().get(), "title", 255));
        }
        if (request.getVenue() != null && request.getVenue().isPresent()) {
            concert.setVenue(requireText(request.getVenue().get(), "venue", 255));
        }
        if (request.getDescription() != null && request.getDescription().isPresent()) {
            concert.setDescription(request.getDescription().get());
        }
        if (request.getArtistName() != null && request.getArtistName().isPresent()) {
            concert.setArtistName(requireText(request.getArtistName().get(), "artist_name", 255));
        }
        if (request.getArtistBio() != null && request.getArtistBio().isPresent()) {
            concert.setArtistBio(request.getArtistBio().get());
        }
        if (request.getCoverImageUrl() != null && request.getCoverImageUrl().isPresent()) {
            concert.setCoverImageUrl(request.getCoverImageUrl().get());
        }
        if (request.getSeatMapUrl() != null && request.getSeatMapUrl().isPresent()) {
            concert.setSeatMapUrl(request.getSeatMapUrl().get());
        }

        if (request.getStartsAt() != null && request.getStartsAt().isPresent()) {
            if (request.getStartsAt().get() == null) throw validation("starts_at cannot be null");
            concert.setStartsAt(request.getStartsAt().get());
        }
        if (request.getEndsAt() != null && request.getEndsAt().isPresent()) {
            if (request.getEndsAt().get() == null) throw validation("ends_at cannot be null");
            concert.setEndsAt(request.getEndsAt().get());
        }
        if (!concert.getEndsAt().isAfter(concert.getStartsAt())) {
            throw new InvalidConcertTimeRangeException();
        }

        if (sameConcert(before, concert)) return concertMapper.toAdminResponse(concert);
        Concert updatedConcert = concertRepository.save(concert);
        audit(currentUser, "UPDATE_CONCERT", "CONCERT", concertId, before, updatedConcert);
        invalidateAfterCommit(concertId);
        return concertMapper.toAdminResponse(updatedConcert);
    }

    @Override
    public AdminConcertResponse publishConcert(User currentUser, UUID concertId) {
        Concert concert = lockConcertAndCheckOwnership(currentUser, concertId);
        if (concert.getStatus() == ConcertStatus.PUBLISHED) return concertMapper.toAdminResponse(concert);
        if (concert.getStatus() != ConcertStatus.DRAFT) throw invalidState();

        List<SeatZone> seatZones = seatZoneRepository.findByConcertIdOrderBySortOrderAsc(concertId);
        if (seatZones.isEmpty()) {
            throw new CannotPublishConcertException("Concert requires at least one seat zone");
        }

        List<TicketType> ticketTypes = ticketTypeRepository.findByConcertId(concertId);
        if (ticketTypes.isEmpty()) {
            throw new CannotPublishConcertException("Concert requires at least one ticket type");
        }

        // Validate zone capacity vs total ticket types quantity per zone
        for (SeatZone zone : seatZones) {
            Long totalAllocated = ticketTypeRepository.sumTotalQuantityByConcertIdAndSeatZoneId(concertId, zone.getId());
            if (totalAllocated != null && totalAllocated > zone.getCapacity()) {
                throw new CannotPublishConcertException(String.format("Zone '%s' (capacity %d) has %d total ticket types quantity allocated, exceeding capacity",
                                zone.getName(), zone.getCapacity(), totalAllocated));
            }
        }

        // Check ticket type sale windows
        for (TicketType tt : ticketTypes) {
            if (!tt.getSaleEndAt().isAfter(tt.getSaleStartAt())) {
                throw new CannotPublishConcertException("Ticket type '" + tt.getName() + "' has invalid sale window (saleEndAt must be after saleStartAt)");
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
        return concertMapper.toAdminResponse(publishedConcert);
    }

    @Override
    public AdminConcertResponse cancelConcert(User currentUser, UUID concertId, String reason) {
        Concert concert = lockConcertAndCheckOwnership(currentUser, concertId);
        if (concert.getStatus() == ConcertStatus.CANCELLED) return concertMapper.toAdminResponse(concert);
        if (concert.getStatus() != ConcertStatus.DRAFT && concert.getStatus() != ConcertStatus.PUBLISHED) throw invalidState();

        Concert before = copyConcert(concert);
        concert.setStatus(ConcertStatus.CANCELLED);
        Concert cancelledConcert = concertRepository.save(concert);
        log.info("Cancelled concert {} by user {}. Reason: {}", cancelledConcert.getId(), currentUser.getEmail(), reason);
        audit(currentUser, "CANCEL_CONCERT", "CONCERT", concertId, before, Map.of("after", cancelledConcert, "reason", reason == null ? "" : reason));
        invalidateAfterCommit(concertId);
        return concertMapper.toAdminResponse(cancelledConcert);
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
            throw new SeatZoneCodeAlreadyExistsException(request.getCode() == null ? "" : request.getCode().trim().toUpperCase(java.util.Locale.ROOT));
        }

        SeatZone zone = seatZoneMapper.toEntity(request);
        zone.setConcert(concert);
        zone.setCode(code);
        if (zone.getSortOrder() == null) {
            zone.setSortOrder(0);
        }

        SeatZone savedZone;
        try {
            savedZone = seatZoneRepository.saveAndFlush(zone);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMessage() != null && exception.getMessage().toLowerCase().contains("code")) {
                throw new SeatZoneCodeAlreadyExistsException(request.getCode() == null ? "" : request.getCode().trim().toUpperCase(java.util.Locale.ROOT));
            }
            throw exception;
        }
        audit(currentUser, "CREATE_SEAT_ZONE", "SEAT_ZONE", savedZone.getId(), null, savedZoneSnapshot(savedZone));
        invalidateAfterCommit(concertId);
        return seatZoneMapper.toAdminResponse(savedZone);
    }

    @Override
    public AdminSeatZoneResponse updateSeatZone(User currentUser, UUID seatZoneId, UpdateSeatZoneRequest request) {
        SeatZone initial = seatZoneRepository.findById(seatZoneId)
                .orElseThrow(() -> new SeatZoneNotFoundException(seatZoneId));
        Concert concert = lockConcertAndCheckOwnership(currentUser, initial.getConcert().getId());
        checkEditable(concert);
        SeatZone zone = seatZoneRepository.findById(seatZoneId).orElseThrow(
                () -> new SeatZoneNotFoundException(seatZoneId));
        Map<String, Object> before = savedZoneSnapshot(zone);

        if (request.getName() != null && request.getName().isPresent()) {
            zone.setName(requireText(request.getName().get(), "name", 100));
        }
        if (request.getDescription() != null && request.getDescription().isPresent()) {
            zone.setDescription(request.getDescription().get());
        }
        if (request.getCapacity() != null && request.getCapacity().isPresent()) {
            if (request.getCapacity().get() == null) throw validation("capacity cannot be null");
            zone.setCapacity(request.getCapacity().get());
        }
        if (request.getSvgPath() != null && request.getSvgPath().isPresent()) {
            zone.setSvgPath(request.getSvgPath().get());
        }
        if (request.getSortOrder() != null && request.getSortOrder().isPresent()) {
            if (request.getSortOrder().get() == null) throw validation("sort_order cannot be null");
            zone.setSortOrder(request.getSortOrder().get());
        }

        Long allocated = ticketTypeRepository.sumTotalQuantityByConcertIdAndSeatZoneId(concert.getId(), zone.getId());
        if (zone.getCapacity() == null || zone.getCapacity() <= 0 || allocated != null && allocated > zone.getCapacity()) {
            throw new ZoneCapacityExceededException();
        }
        if (java.util.Objects.equals(before, savedZoneSnapshot(zone))) return seatZoneMapper.toAdminResponse(zone);
        SeatZone updatedZone = seatZoneRepository.save(zone);
        audit(currentUser, "UPDATE_SEAT_ZONE", "SEAT_ZONE", zone.getId(), before, savedZoneSnapshot(zone));
        invalidateAfterCommit(concert.getId());
        return seatZoneMapper.toAdminResponse(updatedZone);
    }

    @Override
    public AdminTicketTypeResponse createTicketType(User currentUser, UUID concertId, CreateTicketTypeRequest request) {
        Concert concert = lockConcertAndCheckOwnership(currentUser, concertId);
        checkEditable(concert);

        SeatZone seatZone = seatZoneRepository.findByIdAndConcertId(request.getSeatZoneId(), concertId)
                .orElseThrow(() -> new SeatZoneNotFoundException(request.getSeatZoneId()));

        if (request.getName() == null || request.getName().isBlank() || request.getName().length() > 100) throw validation("Invalid ticket type name");
        if (ticketTypeRepository.existsByConcertIdAndName(concertId, request.getName())) {
            throw new TicketTypeNameAlreadyExistsException(request.getName());
        }

        if (!request.getSaleEndAt().isAfter(request.getSaleStartAt())) {
            throw new InvalidSaleWindowException();
        }
        validatePriceAndQuantity(request.getPrice(), request.getTotalQuantity(), request.getMaxPerUser());
        Long allocated = ticketTypeRepository.sumTotalQuantityByConcertIdAndSeatZoneId(concertId, seatZone.getId());
        if (allocated != null && allocated + request.getTotalQuantity() > seatZone.getCapacity()) {
            throw new ZoneCapacityExceededException();
        }

        TicketType ticketType = ticketTypeMapper.toEntity(request);
        ticketType.setConcert(concert);
        ticketType.setSeatZone(seatZone);
        if (ticketType.getCurrency() == null) {
            ticketType.setCurrency("VND");
        }
        ticketType.setHeldQuantity(0);
        ticketType.setSoldQuantity(0);
        ticketType.setStatus(TicketTypeStatus.DRAFT);

        TicketType savedTicketType;
        try {
            savedTicketType = ticketTypeRepository.saveAndFlush(ticketType);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMessage() != null && exception.getMessage().toLowerCase().contains("name")) {
                throw new TicketTypeNameAlreadyExistsException(request.getName());
            }
            throw exception;
        }
        audit(currentUser, "CREATE_TICKET_TYPE", "TICKET_TYPE", savedTicketType.getId(), null, ticketSnapshot(savedTicketType));
        invalidateAfterCommit(concertId);
        return ticketTypeMapper.toAdminResponse(savedTicketType);
    }

    @Override
    public AdminTicketTypeResponse updateTicketType(User currentUser, UUID ticketTypeId, UpdateTicketTypeRequest request) {
        TicketType initial = ticketTypeRepository.findById(ticketTypeId)
                .orElseThrow(() -> new TicketTypeNotFoundException(ticketTypeId));
        Concert concert = lockConcertAndCheckOwnership(currentUser, initial.getConcert().getId());
        checkEditable(concert);
        TicketType ticketType = ticketTypeRepository.findByIdForUpdate(ticketTypeId)
                .orElseThrow(() -> new TicketTypeNotFoundException(ticketTypeId));
        Map<String, Object> before = ticketSnapshot(ticketType);

        if (request.getName() != null && request.getName().isPresent()) {
            ticketType.setName(requireText(request.getName().get(), "name", 100));
        }
        if (!ticketType.getName().equals(before.get("name"))
                && ticketTypeRepository.existsByConcertIdAndNameAndIdNot(concert.getId(), ticketType.getName(), ticketTypeId)) {
            throw new TicketTypeNameAlreadyExistsException(ticketType.getName());
        }
        if (request.getDescription() != null && request.getDescription().isPresent()) {
            ticketType.setDescription(request.getDescription().get());
        }
        if (request.getPrice() != null && request.getPrice().isPresent()) {
            if (request.getPrice().get() == null) throw validation("price cannot be null");
            ticketType.setPrice(request.getPrice().get());
        }
        if (request.getTotalQuantity() != null && request.getTotalQuantity().isPresent()) {
            if (request.getTotalQuantity().get() == null) throw validation("total_quantity cannot be null");
            if (request.getTotalQuantity().get() < (ticketType.getHeldQuantity() + ticketType.getSoldQuantity())) {
                throw new InvalidTicketQuantityException("Total quantity cannot be set lower than the sum of held (" + ticketType.getHeldQuantity() + ") and sold (" + ticketType.getSoldQuantity() + ")");
            }
            ticketType.setTotalQuantity(request.getTotalQuantity().get());
        }
        if (ticketType.getPrice() != null) validatePrice(ticketType.getPrice());
        if (request.getMaxPerUser() != null && request.getMaxPerUser().isPresent()) {
            if (request.getMaxPerUser().get() == null) throw validation("max_per_user cannot be null");
            ticketType.setMaxPerUser(request.getMaxPerUser().get());
        }
        if (request.getSaleStartAt() != null && request.getSaleStartAt().isPresent()) {
            if (request.getSaleStartAt().get() == null) throw validation("sale_start_at cannot be null");
            ticketType.setSaleStartAt(request.getSaleStartAt().get());
        }
        if (request.getSaleEndAt() != null && request.getSaleEndAt().isPresent()) {
            if (request.getSaleEndAt().get() == null) throw validation("sale_end_at cannot be null");
            ticketType.setSaleEndAt(request.getSaleEndAt().get());
        }
        if (!ticketType.getSaleEndAt().isAfter(ticketType.getSaleStartAt())) {
            throw new InvalidSaleWindowException();
        }
        Long allocated = ticketTypeRepository.sumTotalQuantityByConcertIdAndSeatZoneId(concert.getId(), ticketType.getSeatZone().getId());
        if (allocated != null && allocated > ticketType.getSeatZone().getCapacity()) {
            throw new ZoneCapacityExceededException();
        }
        if (request.getStatus() != null && request.getStatus().isPresent()) {
            if (request.getStatus().get() == null) throw validation("status cannot be null");
            try {
                TicketTypeStatus statusEnum = TicketTypeStatus.valueOf(request.getStatus().get().toUpperCase());
                ticketType.setStatus(statusEnum);
            } catch (IllegalArgumentException e) {
                throw new RequestValidationException("VALIDATION_ERROR", "Invalid ticket type status: " + request.getStatus().get());
            }
        }

        if (java.util.Objects.equals(before, ticketSnapshot(ticketType))) return ticketTypeMapper.toAdminResponse(ticketType);
        TicketType updatedTicketType;
        try {
            updatedTicketType = ticketTypeRepository.saveAndFlush(ticketType);
        } catch (DataIntegrityViolationException exception) {
            if (exception.getMessage() != null && exception.getMessage().toLowerCase().contains("name")) {
                throw new TicketTypeNameAlreadyExistsException(ticketType.getName());
            }
            throw exception;
        }
        audit(currentUser, "UPDATE_TICKET_TYPE", "TICKET_TYPE", ticketTypeId, before, ticketSnapshot(updatedTicketType));
        invalidateAfterCommit(concert.getId());
        return ticketTypeMapper.toAdminResponse(updatedTicketType);
    }

    private Concert getConcertAndCheckOwnership(User currentUser, UUID concertId) {
        Concert concert = concertRepository.findById(concertId)
                .orElseThrow(() -> new ConcertNotFoundException(concertId));
        checkConcertOwnership(currentUser, concert);
        return concert;
    }

    private Concert lockConcertAndCheckOwnership(User currentUser, UUID concertId) {
        Concert concert = concertRepository.findByIdForUpdate(concertId)
                .orElseThrow(() -> new ConcertNotFoundException(concertId));
        checkConcertOwnership(currentUser, concert);
        return concert;
    }

    private void checkEditable(Concert concert) {
        if (concert.getStatus() != ConcertStatus.DRAFT && concert.getStatus() != ConcertStatus.PUBLISHED) {
            throw invalidState();
        }
    }

    private ConcertStateConflictException invalidState() {
        return new ConcertStateConflictException();
    }

    private RequestValidationException validation(String message) {
        return new RequestValidationException("VALIDATION_ERROR", message);
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
        com.ticketbox.api.module.audit.domain.entities.AuditAction auditAction;
        try {
            auditAction = com.ticketbox.api.module.audit.domain.entities.AuditAction.valueOf(action);
        } catch (Exception e) {
            auditAction = com.ticketbox.api.module.audit.domain.entities.AuditAction.UPDATE_CONCERT;
        }
        auditLogService.logBusinessSuccess(auditAction, entity, id.toString(), metadata);
    }

    private Map<String, Object> concertSnapshot(Concert c) {
        Map<String, Object> snapshot = new java.util.HashMap<>();
        snapshot.put("id", c.getId().toString()); snapshot.put("title", c.getTitle()); snapshot.put("slug", c.getSlug());
        snapshot.put("venue", c.getVenue()); snapshot.put("description", c.getDescription()); snapshot.put("artist_name", c.getArtistName());
        snapshot.put("artist_bio", c.getArtistBio()); snapshot.put("starts_at", c.getStartsAt()); snapshot.put("ends_at", c.getEndsAt());
        snapshot.put("status", c.getStatus().name());
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
            throw new CatalogAccessDeniedException();
        }
    }}

