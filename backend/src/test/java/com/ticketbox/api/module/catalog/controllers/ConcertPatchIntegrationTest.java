package com.ticketbox.api.module.catalog.controllers;

import com.ticketbox.api.infrastructure.security.JwtUtils;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserRole;
import com.ticketbox.api.module.auth.domain.entities.UserStatus;
import com.ticketbox.api.module.auth.repositories.UserRepository;
import com.ticketbox.api.module.catalog.domain.entities.Concert;
import com.ticketbox.api.module.catalog.domain.entities.ConcertStatus;
import com.ticketbox.api.module.catalog.repositories.ConcertRepository;
import jakarta.servlet.Filter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class ConcertPatchIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private Filter springSecurityFilterChain;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ConcertRepository concertRepository;

    private MockMvc mockMvc;
    private User organizerUser;
    private Concert concert;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilter(springSecurityFilterChain)
                .build();

        organizerUser = userRepository.save(User.builder()
                .email("org_" + UUID.randomUUID() + "@ticketbox.vn")
                .fullName("Organizer Test")
                .role(UserRole.ORGANIZER)
                .status(UserStatus.ACTIVE)
                .build());

        concert = concertRepository.save(Concert.builder()
                .organizer(organizerUser)
                .title("Initial Concert")
                .slug("initial-concert-" + UUID.randomUUID())
                .artistName("Initial Artist")
                .venue("Hanoi Opera House")
                .startsAt(LocalDateTime.now().plusDays(5))
                .endsAt(LocalDateTime.now().plusDays(5).plusHours(3))
                .status(ConcertStatus.DRAFT)
                .build());
    }

    @Test
    void patchConcert_withArtistBio_successfullyDeserializesJsonNullableAndUpdates() throws Exception {
        String token = jwtUtils.generateAccessToken(organizerUser.getId(), organizerUser.getRole().name());
        String bioText = "Sơn Tùng M-TP (tên thật: Nguyễn Thanh Tùng, sinh ngày 05/07/1994) là ca sĩ, nhạc sĩ...";

        String requestBody = "{\"artist_bio\":\"" + bioText + "\"}";

        mockMvc.perform(patch("/admin/concerts/{id}", concert.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.artist_bio").value(bioText));
    }
}
