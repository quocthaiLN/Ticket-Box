package com.ticketbox.api.module.catalog.domain.mappers;

import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserRole;
import com.ticketbox.api.module.catalog.domain.dtos.*;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.ConcertStatus;
import com.ticketbox.api.module.catalog.domain.entities.SeatZone;
import com.ticketbox.api.module.catalog.domain.entities.TicketType;
import com.ticketbox.api.module.catalog.domain.entities.TicketTypeStatus;
import com.ticketbox.api.module.catalog.domain.mappers.ConcertMapper;
import com.ticketbox.api.module.catalog.domain.mappers.SeatZoneMapper;
import com.ticketbox.api.module.catalog.domain.mappers.TicketTypeMapper;
import com.ticketbox.api.module.shared.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CatalogMappersTest {

    private ConcertMapper concertMapper;
    private SeatZoneMapper seatZoneMapper;
    private TicketTypeMapper ticketTypeMapper;
    private StorageService storageService;

    @BeforeEach
    void setUp() {
        concertMapper = new ConcertMapperImpl();
        seatZoneMapper = new SeatZoneMapperImpl();
        ticketTypeMapper = new TicketTypeMapperImpl();
        storageService = mock(StorageService.class);
        when(storageService.buildPublicUrl("cover.png")).thenReturn("http://cdn/cover.png");
        when(storageService.buildPublicUrl("seatmap.svg")).thenReturn("http://cdn/seatmap.svg");
        ReflectionTestUtils.setField(concertMapper, "storageService", storageService);
    }

    @Test
    @DisplayName("ConcertMapper: toAdminResponse maps fields with UTC offset and public URLs")
    void testConcertToAdminResponse() {
        UUID concertId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User organizer = User.builder().id(userId).fullName("Organizer Name").role(UserRole.ORGANIZER).build();
        LocalDateTime now = LocalDateTime.of(2026, 10, 12, 10, 0, 0);

        Concert concert = Concert.builder()
                .id(concertId)
                .title("Rock Fest")
                .slug("rock-fest")
                .venue("Stadium")
                .description("Big show")
                .artistName("Rockers")
                .artistBio("Bio info")
                .startsAt(now)
                .endsAt(now.plusHours(3))
                .createdAt(now.minusDays(1))
                .updatedAt(now.minusHours(2))
                .status(ConcertStatus.PUBLISHED)
                .coverImageUrl("cover.png")
                .seatMapUrl("seatmap.svg")
                .organizer(organizer)
                .build();

        AdminConcertResponse response = concertMapper.toAdminResponse(concert);

        assertThat(response.getId()).isEqualTo(concertId);
        assertThat(response.getTitle()).isEqualTo("Rock Fest");
        assertThat(response.getSlug()).isEqualTo("rock-fest");
        assertThat(response.getVenue()).isEqualTo("Stadium");
        assertThat(response.getArtistName()).isEqualTo("Rockers");
        assertThat(response.getStartsAt()).isEqualTo(now.atOffset(ZoneOffset.UTC));
        assertThat(response.getEndsAt()).isEqualTo(now.plusHours(3).atOffset(ZoneOffset.UTC));
        assertThat(response.getCreatedAt()).isEqualTo(now.minusDays(1).atOffset(ZoneOffset.UTC));
        assertThat(response.getUpdatedAt()).isEqualTo(now.minusHours(2).atOffset(ZoneOffset.UTC));
        assertThat(response.getStatus()).isEqualTo("PUBLISHED");
        assertThat(response.getCoverImageUrl()).isEqualTo("http://cdn/cover.png");
        assertThat(response.getSeatMapUrl()).isEqualTo("http://cdn/seatmap.svg");
        assertThat(response.getOrganizerId()).isEqualTo(userId);
        assertThat(response.getOrganizerName()).isEqualTo("Organizer Name");
    }

    @Test
    @DisplayName("ConcertMapper: toDetailResponse and toResponse map LocalDateTime and basic fields")
    void testConcertToDetailAndPublicResponse() {
        UUID concertId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.of(2026, 10, 12, 10, 0, 0);
        Concert concert = Concert.builder()
                .id(concertId)
                .title("Jazz Night")
                .startsAt(now)
                .endsAt(now.plusHours(2))
                .status(ConcertStatus.PUBLISHED)
                .coverImageUrl("cover.png")
                .seatMapUrl("seatmap.svg")
                .build();

        ConcertDetailResponse detail = concertMapper.toDetailResponse(concert);
        assertThat(detail.getId()).isEqualTo(concertId);
        assertThat(detail.getStartsAt()).isEqualTo(now);
        assertThat(detail.getCoverImageUrl()).isEqualTo("http://cdn/cover.png");

        ConcertResponse publicResp = concertMapper.toResponse(concert);
        assertThat(publicResp.getId()).isEqualTo(concertId);
        assertThat(publicResp.getTitle()).isEqualTo("Jazz Night");
        assertThat(publicResp.getCoverImageUrl()).isEqualTo("http://cdn/cover.png");
    }

    @Test
    @DisplayName("ConcertMapper: toEntity maps CreateConcertRequest")
    void testConcertToEntity() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 1, 19, 0);
        LocalDateTime end = start.plusHours(3);
        CreateConcertRequest req = CreateConcertRequest.builder()
                .title("New Fest")
                .slug("new-fest")
                .venue("Arena")
                .artistName("Artist")
                .startsAt(start)
                .endsAt(end)
                .coverImageUrl("cover.png")
                .build();

        Concert entity = concertMapper.toEntity(req);
        assertThat(entity.getTitle()).isEqualTo("New Fest");
        assertThat(entity.getSlug()).isEqualTo("new-fest");
        assertThat(entity.getVenue()).isEqualTo("Arena");
        assertThat(entity.getArtistName()).isEqualTo("Artist");
        assertThat(entity.getStartsAt()).isEqualTo(start);
        assertThat(entity.getEndsAt()).isEqualTo(end);
        assertThat(entity.getId()).isNull();
        assertThat(entity.getOrganizer()).isNull();
    }

    @Test
    @DisplayName("SeatZoneMapper: entity to DTO and request to entity")
    void testSeatZoneMapper() {
        UUID concertId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        Concert concert = Concert.builder().id(concertId).build();

        SeatZone zone = SeatZone.builder()
                .id(zoneId)
                .concert(concert)
                .code("VIP")
                .name("VIP Area")
                .capacity(100)
                .sortOrder(1)
                .build();

        AdminSeatZoneResponse adminResp = seatZoneMapper.toAdminResponse(zone);
        assertThat(adminResp.getId()).isEqualTo(zoneId);
        assertThat(adminResp.getConcertId()).isEqualTo(concertId);
        assertThat(adminResp.getCode()).isEqualTo("VIP");

        SeatZoneResponse publicResp = seatZoneMapper.toResponse(zone);
        assertThat(publicResp.getId()).isEqualTo(zoneId);
        assertThat(publicResp.getConcertId()).isEqualTo(concertId);
        assertThat(publicResp.getCode()).isEqualTo("VIP");

        CreateSeatZoneRequest req = CreateSeatZoneRequest.builder()
                .code("GA")
                .name("General Admission")
                .capacity(500)
                .sortOrder(2)
                .build();

        SeatZone entity = seatZoneMapper.toEntity(req);
        assertThat(entity.getCode()).isEqualTo("GA");
        assertThat(entity.getName()).isEqualTo("General Admission");
        assertThat(entity.getCapacity()).isEqualTo(500);
        assertThat(entity.getId()).isNull();
    }

    @Test
    @DisplayName("TicketTypeMapper: entity to admin/public DTO and request to entity")
    void testTicketTypeMapper() {
        UUID concertId = UUID.randomUUID();
        UUID zoneId = UUID.randomUUID();
        UUID ticketTypeId = UUID.randomUUID();

        Concert concert = Concert.builder().id(concertId).build();
        SeatZone zone = SeatZone.builder().id(zoneId).code("VIP").build();
        LocalDateTime saleStart = LocalDateTime.of(2026, 10, 1, 9, 0);
        LocalDateTime saleEnd = LocalDateTime.of(2026, 10, 10, 23, 59);

        TicketType tt = TicketType.builder()
                .id(ticketTypeId)
                .concert(concert)
                .seatZone(zone)
                .name("VIP Early Bird")
                .price(new BigDecimal("1500000.00"))
                .currency("VND")
                .totalQuantity(50)
                .heldQuantity(5)
                .soldQuantity(20)
                .maxPerUser(4)
                .saleStartAt(saleStart)
                .saleEndAt(saleEnd)
                .status(TicketTypeStatus.ON_SALE)
                .build();

        AdminTicketTypeResponse adminResp = ticketTypeMapper.toAdminResponse(tt);
        assertThat(adminResp.getId()).isEqualTo(ticketTypeId);
        assertThat(adminResp.getConcertId()).isEqualTo(concertId);
        assertThat(adminResp.getSeatZoneId()).isEqualTo(zoneId);
        assertThat(adminResp.getZoneCode()).isEqualTo("VIP");
        assertThat(adminResp.getSaleStartAt()).isEqualTo(saleStart.atOffset(ZoneOffset.UTC));
        assertThat(adminResp.getStatus()).isEqualTo("ON_SALE");

        TicketTypeResponse publicResp = ticketTypeMapper.toResponse(tt);
        assertThat(publicResp.getId()).isEqualTo(ticketTypeId);
        assertThat(publicResp.getConcertId()).isEqualTo(concertId);
        assertThat(publicResp.getSaleStartAt()).isEqualTo(saleStart);
        assertThat(publicResp.getStatus()).isEqualTo("ON_SALE");

        CreateTicketTypeRequest req = CreateTicketTypeRequest.builder()
                .name("Regular")
                .price(new BigDecimal("500000.00"))
                .totalQuantity(200)
                .maxPerUser(2)
                .saleStartAt(saleStart)
                .saleEndAt(saleEnd)
                .build();

        TicketType entity = ticketTypeMapper.toEntity(req);
        assertThat(entity.getName()).isEqualTo("Regular");
        assertThat(entity.getPrice()).isEqualByComparingTo("500000.00");
        assertThat(entity.getTotalQuantity()).isEqualTo(200);
        assertThat(entity.getId()).isNull();
    }
}
