package com.ticketbox.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ticketbox.api.infrastructure.rateLimit.RateLimitFilter;
import com.ticketbox.api.infrastructure.security.JwtFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootTest
class TicketboxApiApplicationTests {
	@Autowired
	private SecurityFilterChain securityFilterChain;

	@Test
	void contextLoads() {
		var filters = securityFilterChain.getFilters();
		int jwtIndex = -1;
		int rateLimitIndex = -1;
		for (int index = 0; index < filters.size(); index++) {
			if (filters.get(index) instanceof JwtFilter) jwtIndex = index;
			if (filters.get(index) instanceof RateLimitFilter) rateLimitIndex = index;
		}
		assertTrue(jwtIndex >= 0 && rateLimitIndex > jwtIndex);
	}

}
