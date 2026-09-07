package com.ticketbox.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class TicketboxApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(TicketboxApiApplication.class, args);
	}

}