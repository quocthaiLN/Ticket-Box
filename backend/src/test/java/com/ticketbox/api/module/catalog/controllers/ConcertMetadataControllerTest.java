package com.ticketbox.api.module.catalog.controllers;

import com.ticketbox.api.infrastructure.exception.GlobalExceptionHandler;
import com.ticketbox.api.module.artistbio.services.ArtistBioService;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserRole;
import com.ticketbox.api.module.auth.services.CustomUserDetails;
import com.ticketbox.api.module.catalog.domain.dtos.*;
import com.ticketbox.api.module.catalog.domain.exception.CatalogAccessDeniedException;
import com.ticketbox.api.module.catalog.domain.exception.ConcertNotFoundException;
import com.ticketbox.api.module.catalog.services.ConcertService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class ConcertMetadataControllerTest {
    @Mock ConcertService service;
    @Mock ArtistBioService bioService;
    MockMvc mvc;
    final UUID id = UUID.randomUUID();
    final User owner = User.builder().id(UUID.randomUUID()).role(UserRole.ORGANIZER).build();

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new ConcertController(service, bioService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    public boolean supportsParameter(MethodParameter p) { return p.getParameterType() == CustomUserDetails.class; }
                    public Object resolveArgument(MethodParameter p, ModelAndViewContainer m, NativeWebRequest r, WebDataBinderFactory b) {
                        return new CustomUserDetails(owner);
                    }
                }).build();
    }

    @Test
    void returnsPrivateMetadataEnvelope() throws Exception {
        when(service.getConcertMetadata(owner, id)).thenReturn(new AdminConcertMetadataResponse(
                AdminConcertResponse.builder().id(id).status("DRAFT").venue("Hanoi").build(),
                List.of(), List.of(), ConcertMetadataResponse.SeatMapInfo.builder().build(), null));
        mvc.perform(get("/admin/concerts/{id}/metadata", id))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.concert.id").value(id.toString()))
                .andExpect(jsonPath("$.data.concert.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.seat_zones").isArray())
                .andExpect(jsonPath("$.data.ticket_types").isArray())
                .andExpect(jsonPath("$.data.seat_map").exists());
    }

    @Test
    void mapsOwnershipFailureToForbidden() throws Exception {
        when(service.getConcertMetadata(owner, id)).thenThrow(new CatalogAccessDeniedException());
        mvc.perform(get("/admin/concerts/{id}/metadata", id)).andExpect(status().isForbidden());
    }

    @Test
    void mapsMissingConcertToNotFound() throws Exception {
        when(service.getConcertMetadata(owner, id)).thenThrow(new ConcertNotFoundException(id));
        mvc.perform(get("/admin/concerts/{id}/metadata", id)).andExpect(status().isNotFound());
    }
}
