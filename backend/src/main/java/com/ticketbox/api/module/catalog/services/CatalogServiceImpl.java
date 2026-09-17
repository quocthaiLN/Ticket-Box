package com.ticketbox.api.module.catalog.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.catalog.domain.dtos.*;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.ConcertStatus;
import com.ticketbox.api.module.catalog.domain.entities.SeatZone;
import com.ticketbox.api.module.catalog.domain.entities.TicketType;
import com.ticketbox.api.module.catalog.domain.entities.TicketTypeStatus;
import com.ticketbox.api.module.catalog.repositories.ConcertRepository;
import com.ticketbox.api.module.catalog.repositories.SeatZoneRepository;
import com.ticketbox.api.module.catalog.repositories.TicketTypeRepository;
import com.ticketbox.api.module.order.domain.entities.UserTicketTypeCounter;
import com.ticketbox.api.module.order.repositories.UserTicketTypeCounterRepository;
import com.ticketbox.api.module.shared.cache.CachedPage;
import com.ticketbox.api.module.shared.storage.StorageService;
import com.ticketbox.api.module.shared.cache.CacheService;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CatalogServiceImpl implements CatalogService {

    private final ConcertRepository concertRepository;
    private final SeatZoneRepository seatZoneRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final UserTicketTypeCounterRepository counterRepository;
    private final CacheService cacheService;
    private final StorageService storageService;

    @Override
    public Page<ConcertResponse> getPublishedConcerts(String q, String city, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("q", q);
        params.put("city", city);
        params.put("from", from != null ? from.toString() : null);
        params.put("to", to != null ? to.toString() : null);
        params.put("page", pageable.getPageNumber());
        params.put("size", pageable.getPageSize());
        params.put("sort", pageable.getSort().toString());

        String cacheKey = cacheService.generateHashKey("concerts:all", params);
        TypeReference<CachedPage<ConcertResponse>> typeRef = new TypeReference<>() {};

        CachedPage<ConcertResponse> cachedPage = cacheService.getOrFetch(cacheKey, Duration.ofHours(1), typeRef, () -> {
            Specification<Concert> spec = (root, query, cb) -> {
                List<Predicate> predicates = new ArrayList<>();

                predicates.add(cb.equal(root.get("status"), ConcertStatus.PUBLISHED));

                if (q != null && !q.trim().isEmpty()) {
                    String pattern = "%" + q.trim().toLowerCase() + "%";
                    Predicate titleMatch = cb.like(cb.lower(root.get("title")), pattern);
                    Predicate artistMatch = cb.like(cb.lower(root.get("artistName")), pattern);
                    predicates.add(cb.or(titleMatch, artistMatch));
                }

                if (city != null && !city.trim().isEmpty()) {
                    predicates.add(cb.like(cb.lower(root.get("venue")), "%" + city.trim().toLowerCase() + "%"));
                }

                if (from != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("startsAt"), from));
                }

                if (to != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("startsAt"), to));
                }

                return cb.and(predicates.toArray(new Predicate[0]));
            };

            Page<Concert> concertPage = concertRepository.findAll(spec, pageable);
            Page<ConcertResponse> dtoPage = concertPage.map(this::mapToConcertResponse);
            return CachedPage.from(dtoPage);
        });

        return cachedPage.toPage(pageable);
    }

    @Override
    public ConcertDetailResponse getConcertDetail(UUID concertId) {
        String cacheKey = "concerts:" + concertId;
        return cacheService.getOrFetch(cacheKey, Duration.ofMinutes(30), ConcertDetailResponse.class, () -> {
            Concert concert = concertRepository.findById(concertId)
                    .filter(c -> c.getStatus() == ConcertStatus.PUBLISHED)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "CONCERT_NOT_FOUND", "Concert not found or not published with ID: " + concertId));

            return mapToConcertDetailResponse(concert);
        });
    }

    @Override
    public ConcertMetadataResponse getConcertMetadata(UUID concertId) {
        String cacheKey = "concerts:" + concertId + ":metadata";
        return cacheService.getOrFetch(cacheKey, Duration.ofHours(24), ConcertMetadataResponse.class, () -> {
            Concert concert = concertRepository.findById(concertId)
                    .filter(c -> c.getStatus() == ConcertStatus.PUBLISHED)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "CONCERT_NOT_FOUND", "Concert not found with ID: " + concertId));

            List<SeatZone> seatZones = seatZoneRepository.findByConcertIdOrderBySortOrderAsc(concertId);
            List<TicketType> ticketTypes = ticketTypeRepository.findByConcertId(concertId);

            List<SeatZoneResponse> zoneResponses = seatZones.stream().map(this::mapToSeatZoneResponse).collect(Collectors.toList());
            List<TicketTypeResponse> ticketResponses = ticketTypes.stream().map(this::mapToTicketTypeResponse).collect(Collectors.toList());

            ConcertMetadataResponse.SeatMapInfo seatMapInfo = ConcertMetadataResponse.SeatMapInfo.builder()
                    .svgUrl(storageService.buildPublicUrl(concert.getSeatMapUrl()))
                    .fallbackImageUrl(storageService.buildPublicUrl(concert.getCoverImageUrl()))
                    .build();

            return ConcertMetadataResponse.builder()
                    .concert(mapToConcertDetailResponse(concert))
                    .seatZones(zoneResponses)
                    .ticketTypes(ticketResponses)
                    .seatMap(seatMapInfo)
                    .artistBio(concert.getArtistBio())
                    .build();
        });
    }

    @Override
    public SeatMapResponse getConcertSeatMap(UUID concertId) {
        String cacheKey = "concerts:" + concertId + ":seatmap";
        return cacheService.getOrFetch(cacheKey, Duration.ofHours(1), SeatMapResponse.class, () -> {
            Concert concert = concertRepository.findById(concertId)
                    .filter(c -> c.getStatus() == ConcertStatus.PUBLISHED)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "CONCERT_NOT_FOUND", "Concert not found with ID: " + concertId));

            List<SeatZone> seatZones = seatZoneRepository.findByConcertIdOrderBySortOrderAsc(concertId);
            List<SeatZoneResponse> zoneResponses = seatZones.stream().map(this::mapToSeatZoneResponse).collect(Collectors.toList());

            return SeatMapResponse.builder()
                    .concertId(concert.getId())
                    .svgUrl(storageService.buildPublicUrl(concert.getSeatMapUrl()))
                    .fallbackImageUrl(storageService.buildPublicUrl(concert.getCoverImageUrl()))
                    .zones(zoneResponses)
                    .build();
        });
    }

    @Override
    public List<TicketTypeResponse> getTicketTypes(UUID concertId, boolean includeClosed) {
        String cacheKey = cacheService.generateHashKey("concerts:" + concertId + ":ticket-types", Map.of("includeClosed", includeClosed));
        TypeReference<List<TicketTypeResponse>> typeRef = new TypeReference<>() {};

        return cacheService.getOrFetch(cacheKey, Duration.ofMinutes(30), typeRef, () -> {
            Concert concert = concertRepository.findById(concertId)
                    .filter(c -> c.getStatus() == ConcertStatus.PUBLISHED)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "CONCERT_NOT_FOUND", "Concert not found with ID: " + concertId));

            List<TicketType> ticketTypes = ticketTypeRepository.findByConcertId(concert.getId());

            if (!includeClosed) {
                ticketTypes = ticketTypes.stream()
                        .filter(t -> t.getStatus() != TicketTypeStatus.SUSPENDED && t.getStatus() != TicketTypeStatus.DRAFT)
                        .collect(Collectors.toList());
            }

            return ticketTypes.stream().map(this::mapToTicketTypeResponse).collect(Collectors.toList());
        });
    }

    @Override
    public InventoryResponse getInventory(UUID concertId) {
        String cacheKey = "concerts:" + concertId + ":inventory";
        return cacheService.getOrFetch(cacheKey, Duration.ofMinutes(5), InventoryResponse.class, () -> {
            Concert concert = concertRepository.findById(concertId)
                    .filter(c -> c.getStatus() == ConcertStatus.PUBLISHED)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "CONCERT_NOT_FOUND", "Concert not found with ID: " + concertId));

            List<TicketType> ticketTypes = ticketTypeRepository.findByConcertId(concert.getId());
            List<InventoryResponse.InventoryItem> items = new ArrayList<>();

            for (TicketType tt : ticketTypes) {
                int available = tt.getAvailableQuantity();
                String statusStr = tt.getStatus().name();
                String displayStatus;

                if (tt.getStatus() == TicketTypeStatus.CLOSED || tt.getStatus() == TicketTypeStatus.SUSPENDED) {
                    displayStatus = "CLOSED";
                } else if (available <= 0 || tt.getStatus() == TicketTypeStatus.SOLD_OUT) {
                    displayStatus = "SOLD_OUT";
                } else if (available <= 10) {
                    displayStatus = "LOW_STOCK";
                } else {
                    displayStatus = "AVAILABLE";
                }

                items.add(InventoryResponse.InventoryItem.builder()
                        .ticketTypeId(tt.getId())
                        .seatZoneId(tt.getSeatZone().getId())
                        .zoneCode(tt.getSeatZone().getCode())
                        .availableQuantity(available)
                        .status(statusStr)
                        .displayStatus(displayStatus)
                        .build());
            }

            return InventoryResponse.builder()
                    .concertId(concert.getId())
                    .asOf(LocalDateTime.now())
                    .items(items)
                    .build();
        });
    }

    @Override
    public ConcertQuotaResponse getQuota(User currentUser, UUID concertId) {
        Concert concert = concertRepository.findById(concertId)
                .filter(c -> c.getStatus() == ConcertStatus.PUBLISHED)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "CONCERT_NOT_FOUND", "Concert not found with ID: " + concertId));

        Map<UUID, UserTicketTypeCounter> countersByTicketTypeId = counterRepository
                .findByUserIdAndConcertId(currentUser.getId(), concertId)
                .stream()
                .collect(Collectors.toMap(counter -> counter.getTicketType().getId(), counter -> counter));

        List<TicketTypeQuotaResponse> items = ticketTypeRepository.findByConcertId(concert.getId()).stream()
                .map(ticketType -> mapToTicketTypeQuotaResponse(ticketType, countersByTicketTypeId.get(ticketType.getId())))
                .toList();

        return ConcertQuotaResponse.builder()
                .concertId(concert.getId())
                .items(items)
                .build();
    }

    private ConcertResponse mapToConcertResponse(Concert concert) {
        List<TicketType> ticketTypes = ticketTypeRepository.findByConcertId(concert.getId());

        BigDecimal minPrice = ticketTypes.stream().map(TicketType::getPrice).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal maxPrice = ticketTypes.stream().map(TicketType::getPrice).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        String currency = ticketTypes.stream().findFirst().map(TicketType::getCurrency).orElse("VND");

        return ConcertResponse.builder()
                .id(concert.getId())
                .title(concert.getTitle())
                .slug(concert.getSlug())
                .venue(concert.getVenue())
                .artistName(concert.getArtistName())
                .startsAt(concert.getStartsAt())
                .endsAt(concert.getEndsAt())
                .status(concert.getStatus().name())
                .coverImageUrl(storageService.buildPublicUrl(concert.getCoverImageUrl()))
                .organizerId(concert.getOrganizer() != null ? concert.getOrganizer().getId() : null)
                .organizerName(concert.getOrganizer() != null ? concert.getOrganizer().getFullName() : null)
                .ticketPriceRange(ConcertResponse.TicketPriceRange.builder()
                        .minAmount(minPrice)
                        .maxAmount(maxPrice)
                        .currency(currency)
                        .build())
                .build();
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

    private TicketTypeQuotaResponse mapToTicketTypeQuotaResponse(TicketType ticketType, UserTicketTypeCounter counter) {
        int heldQuantity = counter != null ? counter.getHeldQuantity() : 0;
        int paidQuantity = counter != null ? counter.getPaidQuantity() : 0;
        int remainingQuantity = Math.max(0, ticketType.getMaxPerUser() - heldQuantity - paidQuantity);

        return TicketTypeQuotaResponse.builder()
                .ticketTypeId(ticketType.getId())
                .heldQuantity(heldQuantity)
                .maxPerUser(ticketType.getMaxPerUser())
                .paidQuantity(paidQuantity)
                .remainingQuantity(remainingQuantity)
                .build();
    }
}
