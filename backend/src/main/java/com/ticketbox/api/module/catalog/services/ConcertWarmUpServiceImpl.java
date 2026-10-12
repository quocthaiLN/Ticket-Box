package com.ticketbox.api.module.catalog.services;

import com.ticketbox.api.module.catalog.domain.dtos.*;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.ConcertStatus;
import com.ticketbox.api.module.catalog.domain.entities.SeatZone;
import com.ticketbox.api.module.catalog.domain.entities.TicketType;
import com.ticketbox.api.module.catalog.domain.entities.TicketTypeStatus;
import com.ticketbox.api.module.catalog.domain.mappers.ConcertMapper;
import com.ticketbox.api.module.catalog.domain.mappers.SeatZoneMapper;
import com.ticketbox.api.module.catalog.domain.mappers.TicketTypeMapper;
import com.ticketbox.api.module.catalog.repositories.ConcertRepository;
import com.ticketbox.api.module.catalog.repositories.SeatZoneRepository;
import com.ticketbox.api.module.catalog.repositories.TicketTypeRepository;
import com.ticketbox.api.module.shared.cache.CacheService;
import com.ticketbox.api.module.shared.storage.StorageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final StorageService storageService;
    private final ConcertMapper concertMapper;
    private final SeatZoneMapper seatZoneMapper;
    private final TicketTypeMapper ticketTypeMapper;

    @Override
    public void warmUpConcertCache(UUID concertId) {
        log.info("Starting cache warm-up for concertId: {}", concertId);

        Concert concert = concertRepository.findById(concertId)
                .filter(c -> c.getStatus() == ConcertStatus.PUBLISHED)
                .orElse(null);

        if (concert == null) {
            log.warn("Concert not found or not published for ID: {}. Skipping warm-up.", concertId);
            return;
        }

        List<SeatZone> seatZones = seatZoneRepository.findByConcertIdOrderBySortOrderAsc(concertId);
        List<TicketType> ticketTypes = ticketTypeRepository.findByConcertId(concertId);

        List<SeatZoneResponse> zoneResponses = seatZones.stream()
                .map(seatZoneMapper::toResponse)
                .collect(Collectors.toList());

        List<TicketTypeResponse> ticketResponses = ticketTypes.stream()
                .map(ticketTypeMapper::toResponse)
                .collect(Collectors.toList());

        // 1. Warm-up Concert Detail (concerts:{concertId}) - 30m TTL
        ConcertDetailResponse detailResponse = concertMapper.toDetailResponse(concert);
        cacheService.set("concerts:" + concertId, detailResponse, Duration.ofMinutes(30));

        // 2. Warm-up Concert Metadata (concerts:{concertId}:metadata) - 24h TTL
        ConcertMetadataResponse.SeatMapInfo seatMapInfo = ConcertMetadataResponse.SeatMapInfo.builder()
                .svgUrl(storageService.buildPublicUrl(concert.getSeatMapUrl()))
                .fallbackImageUrl(storageService.buildPublicUrl(concert.getCoverImageUrl()))
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
                .svgUrl(storageService.buildPublicUrl(concert.getSeatMapUrl()))
                .fallbackImageUrl(storageService.buildPublicUrl(concert.getCoverImageUrl()))
                .zones(zoneResponses)
                .build();
        cacheService.set("concerts:" + concertId + ":seatmap", seatMapResponse, Duration.ofHours(1));

        // 4. Warm-up Ticket Types (concerts:{concertId}:ticket-types includeClosed=false) - 30m TTL
        List<TicketTypeResponse> openTicketResponses = ticketTypes.stream()
                .filter(t -> t.getStatus() != TicketTypeStatus.SUSPENDED && t.getStatus() != TicketTypeStatus.DRAFT)
                .map(ticketTypeMapper::toResponse)
                .collect(Collectors.toList());

        String ticketTypesCacheKey = cacheService.generateHashKey("concerts:" + concertId + ":ticket-types", Map.of("includeClosed", false));
        cacheService.set(ticketTypesCacheKey, openTicketResponses, Duration.ofMinutes(30));

        // 5. Warm-up Inventory Response (concerts:{concertId}:inventory) - 5m TTL
        List<InventoryResponse.InventoryItem> inventoryItems = new ArrayList<>();
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

        log.info("Finished cache warm-up successfully for concertId: {}", concertId);
    }
}
