package com.ticketbox.api.module.catalog.services;

import com.ticketbox.api.module.catalog.domain.dtos.*;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.SeatZone;
import com.ticketbox.api.module.catalog.domain.entities.TicketType;
import com.ticketbox.api.module.catalog.repositories.ConcertRepository;
import com.ticketbox.api.module.catalog.repositories.SeatZoneRepository;
import com.ticketbox.api.module.catalog.repositories.TicketTypeRepository;
import com.ticketbox.api.module.shared.cache.CacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConcertWarmUpServiceImpl implements ConcertWarmUpService {

    private final ConcertRepository concertRepository;
    private final SeatZoneRepository seatZoneRepository;
    private final TicketTypeRepository ticketTypeRepository;
    private final CacheService cacheService;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public void warmUpConcertCache(UUID concertId) {
        log.info("Starting cache warm-up for concertId: {}", concertId);

        Concert concert = concertRepository.findById(concertId)
                .filter(c -> c.getStatus() == Concert.ConcertStatus.PUBLISHED)
                .orElse(null);

        if (concert == null) {
            log.warn("Concert not found or not published for ID: {}. Skipping warm-up.", concertId);
            return;
        }

        List<SeatZone> seatZones = seatZoneRepository.findByConcertIdOrderBySortOrderAsc(concertId);
        List<TicketType> ticketTypes = ticketTypeRepository.findByConcertId(concertId);

        List<SeatZoneResponse> zoneResponses = seatZones.stream()
                .map(this::mapToSeatZoneResponse)
                .collect(Collectors.toList());

        List<TicketTypeResponse> ticketResponses = ticketTypes.stream()
                .map(this::mapToTicketTypeResponse)
                .collect(Collectors.toList());

        // 1. Warm-up Concert Detail (concerts:{concertId}) - 30m TTL
        ConcertDetailResponse detailResponse = mapToConcertDetailResponse(concert);
        cacheService.set("concerts:" + concertId, detailResponse, Duration.ofMinutes(30));

        // 2. Warm-up Concert Metadata (concerts:{concertId}:metadata) - 24h TTL
        ConcertMetadataResponse.SeatMapInfo seatMapInfo = ConcertMetadataResponse.SeatMapInfo.builder()
                .svgUrl(concert.getSeatMapUrl())
                .fallbackImageUrl(concert.getCoverImageUrl())
                .build();

        ConcertMetadataResponse metadataResponse = ConcertMetadataResponse.builder()
                .concert(detailResponse)
                .seatZones(zoneResponses)
                .ticketTypes(ticketResponses)
                .seatMap(seatMapInfo)
                .artistBio(concert.getArtistBio())
                .build();
        cacheService.set("concerts:" + concertId + ":metadata", metadataResponse, Duration.ofHours(24));

        // 3. Warm-up Concert SeatMap (concerts:{concertId}:seatmap) - 1h TTL
        SeatMapResponse seatMapResponse = SeatMapResponse.builder()
                .concertId(concert.getId())
                .svgUrl(concert.getSeatMapUrl())
                .fallbackImageUrl(concert.getCoverImageUrl())
                .zones(zoneResponses)
                .build();
        cacheService.set("concerts:" + concertId + ":seatmap", seatMapResponse, Duration.ofHours(1));

        // 4. Warm-up Ticket Types (concerts:{concertId}:ticket-types includeClosed=false) - 30m TTL
        List<TicketTypeResponse> openTicketResponses = ticketTypes.stream()
                .filter(t -> t.getStatus() != TicketType.TicketTypeStatus.SUSPENDED && t.getStatus() != TicketType.TicketTypeStatus.DRAFT)
                .map(this::mapToTicketTypeResponse)
                .collect(Collectors.toList());

        String ticketTypesCacheKey = cacheService.generateHashKey("concerts:" + concertId + ":ticket-types", Map.of("includeClosed", false));
        cacheService.set(ticketTypesCacheKey, openTicketResponses, Duration.ofMinutes(30));

        // 5. Warm-up Inventory Response (concerts:{concertId}:inventory) - 5m TTL & Redis Hash Snapshot
        List<InventoryResponse.InventoryItem> inventoryItems = new ArrayList<>();
        for (TicketType tt : ticketTypes) {
            int available = tt.getAvailableQuantity();
            String statusStr = tt.getStatus().name();
            String displayStatus;

            if (tt.getStatus() == TicketType.TicketTypeStatus.CLOSED || tt.getStatus() == TicketType.TicketTypeStatus.SUSPENDED) {
                displayStatus = "CLOSED";
            } else if (available <= 0 || tt.getStatus() == TicketType.TicketTypeStatus.SOLD_OUT) {
                displayStatus = "SOLD_OUT";
            } else if (available <= 10) {
                displayStatus = "LOW_STOCK";
            } else {
                displayStatus = "AVAILABLE";
            }

            inventoryItems.add(InventoryResponse.InventoryItem.builder()
                    .ticketTypeId(tt.getId())
                    .seatZoneId(tt.getSeatZone().getId())
                    .zoneCode(tt.getSeatZone().getCode())
                    .availableQuantity(available)
                    .status(statusStr)
                    .displayStatus(displayStatus)
                    .build());
        }

        InventoryResponse inventoryResponse = InventoryResponse.builder()
                .concertId(concert.getId())
                .asOf(LocalDateTime.now())
                .items(inventoryItems)
                .build();
        cacheService.set("concerts:" + concertId + ":inventory", inventoryResponse, Duration.ofMinutes(5));

        // Write inventory hash snapshot to Redis
        try {
            String redisHashKey = "inventory:concert:" + concertId;
            for (InventoryResponse.InventoryItem item : inventoryItems) {
                stringRedisTemplate.opsForHash().put(redisHashKey, item.getTicketTypeId().toString(), String.valueOf(item.getAvailableQuantity()));
            }
        } catch (Exception e) {
            log.warn("Failed to write Redis inventory hash snapshot during warm-up for concertId {}: {}", concertId, e.getMessage());
        }

        log.info("Finished cache warm-up successfully for concertId: {}", concertId);
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
                .coverImageUrl(concert.getCoverImageUrl())
                .seatMapUrl(concert.getSeatMapUrl())
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
