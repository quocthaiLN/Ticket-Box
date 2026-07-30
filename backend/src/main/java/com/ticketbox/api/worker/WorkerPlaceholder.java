package com.ticketbox.api.worker;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("worker")
public class WorkerPlaceholder implements CommandLineRunner {

    @Override
    public void run(String... args) {
        log.info("=================================================");
        log.info(">>> TicketBox Worker Process Initialized <<<");
        log.info("Active Profile: worker | Listening for Async Jobs");
        log.info("=================================================");
    }
}
