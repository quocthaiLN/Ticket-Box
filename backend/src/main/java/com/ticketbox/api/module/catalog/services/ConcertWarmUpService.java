package com.ticketbox.api.module.catalog.services;

import java.util.UUID;

public interface ConcertWarmUpService {

    void warmUpConcertCache(UUID concertId);
}
