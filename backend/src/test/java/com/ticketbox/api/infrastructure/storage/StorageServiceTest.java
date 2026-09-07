package com.ticketbox.api.infrastructure.storage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ticketbox.api.module.shared.storage.StorageService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StorageServiceTest {

    @Test
    @DisplayName("Returns null or blank when path is null or blank")
    void buildPublicUrl_nullOrBlank() {
        StorageService service = new StorageService("http://localhost:9000/ticket-box-storage");

        assertNull(service.buildPublicUrl(null));
        assertEquals("", service.buildPublicUrl(""));
        assertEquals("   ", service.buildPublicUrl("   "));
    }

    @Test
    @DisplayName("Returns original URL if already absolute")
    void buildPublicUrl_alreadyAbsolute() {
        StorageService service = new StorageService("http://localhost:9000/ticket-box-storage");

        assertEquals("http://external.com/pic.png", service.buildPublicUrl("http://external.com/pic.png"));
        assertEquals("https://external.com/pic.svg", service.buildPublicUrl("https://external.com/pic.svg"));
    }

    @Test
    @DisplayName("Prepend base URL cleanly to relative path")
    void buildPublicUrl_relativePath() {
        StorageService service = new StorageService("http://localhost:9000/ticket-box-storage");

        assertEquals(
                "http://localhost:9000/ticket-box-storage/concerts/ats-2026-cover.jpg",
                service.buildPublicUrl("concerts/ats-2026-cover.jpg")
        );
    }

    @Test
    @DisplayName("Avoid double slash when base URL or relative path contains leading/trailing slash")
    void buildPublicUrl_slashHandling() {
        StorageService serviceWithSlash = new StorageService("http://localhost:9000/ticket-box-storage/");

        assertEquals(
                "http://localhost:9000/ticket-box-storage/concerts/ats-2026-cover.jpg",
                serviceWithSlash.buildPublicUrl("concerts/ats-2026-cover.jpg")
        );

        assertEquals(
                "http://localhost:9000/ticket-box-storage/concerts/ats-2026-cover.jpg",
                serviceWithSlash.buildPublicUrl("/concerts/ats-2026-cover.jpg")
        );
    }
}
