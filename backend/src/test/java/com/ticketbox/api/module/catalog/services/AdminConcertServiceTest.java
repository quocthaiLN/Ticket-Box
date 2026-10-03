package com.ticketbox.api.module.catalog.services;

import com.ticketbox.api.infrastructure.exception.AppException;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserRole;
import com.ticketbox.api.module.catalog.domain.dtos.AdminConcertResponse;
import com.ticketbox.api.module.catalog.domain.dtos.CreateConcertRequest;
import com.ticketbox.api.module.catalog.domain.dtos.UpdateConcertRequest;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.ConcertStatus;
import com.ticketbox.api.module.catalog.repositories.ConcertRepository;
import com.ticketbox.api.module.catalog.repositories.SeatZoneRepository;
import com.ticketbox.api.module.catalog.repositories.TicketTypeRepository;
import com.ticketbox.api.module.shared.storage.StorageService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConcertServiceTest {

    @Mock
    private ConcertRepository concertRepository;

    @Mock
    private SeatZoneRepository seatZoneRepository;

    @Mock
    private TicketTypeRepository ticketTypeRepository;

    @Mock
    private com.ticketbox.api.module.shared.cache.CacheService cacheService;

    @Mock
    private StorageService storageService;

    @Mock
    private com.ticketbox.api.module.audit.services.AuditLogService auditLogService;

    @InjectMocks
    private ConcertServiceImpl concertService;

    private User organizerUser;
    private User otherOrganizerUser;
    private User adminUser;
    private Concert concert;
    private UUID concertId;

    @BeforeEach
    void setUp() {
        organizerUser = User.builder()
                .id(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .email("organizer@ticketbox.com")
                .role(UserRole.ORGANIZER)
                .build();

        otherOrganizerUser = User.builder()
                .id(UUID.fromString("99999999-9999-9999-9999-999999999999"))
                .email("other@ticketbox.com")
                .role(UserRole.ORGANIZER)
                .build();

        adminUser = User.builder()
                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .email("admin@ticketbox.com")
                .role(UserRole.ADMIN)
                .build();

        concertId = UUID.randomUUID();
        concert = Concert.builder()
                .id(concertId)
                .title("Test Concert")
                .slug("test-concert")
                .venue("Sân vận động Mỹ Đình")
                .artistName("Test Artist")
                .startsAt(LocalDateTime.now().plusDays(10))
                .endsAt(LocalDateTime.now().plusDays(10).plusHours(3))
                .status(ConcertStatus.DRAFT)
                .organizer(organizerUser)
                .build();
    }

    @Test
    void metadata_ownerCanReadDraftWithEmptyCollections() {
        when(concertRepository.findById(concertId)).thenReturn(Optional.of(concert));
        var result = concertService.getConcertMetadata(organizerUser, concertId);
        assertEquals(concertId, result.concert().getId());
        assertEquals("DRAFT", result.concert().getStatus());
        assertTrue(result.seatZones().isEmpty());
        assertTrue(result.ticketTypes().isEmpty());
        verifyNoInteractions(cacheService);
    }

    @Test
    void metadata_adminCanReadPublishedWithInventory() {
        concert.setStatus(ConcertStatus.PUBLISHED);
        var zone = com.ticketbox.api.module.catalog.domain.entities.SeatZone.builder()
                .id(UUID.randomUUID()).concert(concert).code("VIP").name("VIP")
                .capacity(100).sortOrder(0).build();
        var ticket = com.ticketbox.api.module.catalog.domain.entities.TicketType.builder()
                .id(UUID.randomUUID()).concert(concert).seatZone(zone).name("VIP ticket")
                .price(java.math.BigDecimal.TEN).currency("VND").totalQuantity(50)
                .heldQuantity(3).soldQuantity(7).maxPerUser(2)
                .saleStartAt(concert.getStartsAt().minusDays(2)).saleEndAt(concert.getStartsAt())
                .status(com.ticketbox.api.module.catalog.domain.entities.TicketTypeStatus.ON_SALE).build();
        when(concertRepository.findById(concertId)).thenReturn(Optional.of(concert));
        when(seatZoneRepository.findByConcertIdOrderBySortOrderAsc(concertId)).thenReturn(java.util.List.of(zone));
        when(ticketTypeRepository.findByConcertId(concertId)).thenReturn(java.util.List.of(ticket));
        var result = concertService.getConcertMetadata(adminUser, concertId);
        assertEquals("PUBLISHED", result.concert().getStatus());
        assertEquals(zone.getId(), result.seatZones().getFirst().getId());
        assertEquals(40, result.ticketTypes().getFirst().getAvailableQuantity());
        assertEquals(3, result.ticketTypes().getFirst().getHeldQuantity());
    }

    @Test
    void metadata_otherOrganizerCannotReadChildren() {
        when(concertRepository.findById(concertId)).thenReturn(Optional.of(concert));
        assertThrows(com.ticketbox.api.module.catalog.domain.exception.CatalogAccessDeniedException.class,
                () -> concertService.getConcertMetadata(otherOrganizerUser, concertId));
        verifyNoInteractions(seatZoneRepository, ticketTypeRepository);
    }

    @Test
    void metadata_missingConcertIsNotFound() {
        when(concertRepository.findById(concertId)).thenReturn(Optional.empty());
        assertThrows(com.ticketbox.api.module.catalog.domain.exception.ConcertNotFoundException.class,
                () -> concertService.getConcertMetadata(organizerUser, concertId));
    }

    @Test
    @DisplayName("Create concert successfully by Organizer")
    void createConcert_success() {
        CreateConcertRequest request = CreateConcertRequest.builder()
                .title("New Concert")
                .slug("new-concert")
                .venue("Phú Thọ")
                .artistName("Artist")
                .startsAt(LocalDateTime.now().plusDays(5))
                .endsAt(LocalDateTime.now().plusDays(5).plusHours(2))
                .build();

        when(concertRepository.existsBySlug("new-concert")).thenReturn(false);
        when(concertRepository.saveAndFlush(any(Concert.class))).thenAnswer(invocation -> {
            Concert c = invocation.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        AdminConcertResponse response = concertService.createConcert(organizerUser, request);

        assertNotNull(response);
        assertEquals("New Concert", response.getTitle());
        assertEquals("DRAFT", response.getStatus());
        assertEquals(organizerUser.getId(), response.getOrganizerId());
        verify(concertRepository, times(1)).saveAndFlush(any(Concert.class));
    }

    @Test
    @DisplayName("Organizer updating another organizer's concert throws FORBIDDEN")
    void updateConcert_forbidden_differentOrganizer() {
        UpdateConcertRequest request = UpdateConcertRequest.builder()
                .title("Updated Title")
                .build();

        when(concertRepository.findByIdForUpdate(concertId)).thenReturn(Optional.of(concert));

        AppException ex = assertThrows(AppException.class, () ->
                concertService.updateConcert(otherOrganizerUser, concertId, request)
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
        assertEquals("FORBIDDEN", ex.getErrorCode());
    }

    @Test
    @DisplayName("Admin updating any organizer's concert succeeds")
    void updateConcert_adminSuccess() {
        UpdateConcertRequest request = UpdateConcertRequest.builder()
                .title("Admin Updated Title")
                .build();

        when(concertRepository.findByIdForUpdate(concertId)).thenReturn(Optional.of(concert));
        when(concertRepository.save(any(Concert.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminConcertResponse response = concertService.updateConcert(adminUser, concertId, request);

        assertNotNull(response);
        assertEquals("Admin Updated Title", response.getTitle());
    }

    @Test
    @DisplayName("Repeated publish on a published concert is side-effect free")
    void publishConcert_alreadyPublished() {
        concert.setStatus(ConcertStatus.PUBLISHED);
        when(concertRepository.findByIdForUpdate(concertId)).thenReturn(Optional.of(concert));

        AdminConcertResponse response = concertService.publishConcert(adminUser, concertId);

        assertEquals("PUBLISHED", response.getStatus());
        verifyNoInteractions(seatZoneRepository, ticketTypeRepository, auditLogService, cacheService);
        verify(concertRepository, never()).save(any(Concert.class));
    }

    @Test
    @DisplayName("Completed concert cannot be edited")
    void updateConcert_completedConcertConflicts() {
        concert.setStatus(ConcertStatus.COMPLETED);
        when(concertRepository.findByIdForUpdate(concertId)).thenReturn(Optional.of(concert));

        AppException ex = assertThrows(AppException.class, () ->
                concertService.updateConcert(adminUser, concertId, UpdateConcertRequest.builder().title("Nope").build()));

        assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        assertEquals("INVALID_CONCERT_STATE", ex.getErrorCode());
        verify(concertRepository, never()).save(any(Concert.class));
    }

    @Test
    @DisplayName("Repeated cancel on cancelled concert does not repeat side effects")
    void cancelConcert_alreadyCancelled() {
        concert.setStatus(ConcertStatus.CANCELED);
        when(concertRepository.findByIdForUpdate(concertId)).thenReturn(Optional.of(concert));

        AdminConcertResponse response = concertService.cancelConcert(adminUser, concertId, "retry");

        assertEquals("CANCELED", response.getStatus());
        verify(concertRepository, never()).save(any(Concert.class));
        verifyNoInteractions(auditLogService, cacheService);
    }
}
