package com.ticketbox.api.module.auth;

import com.ticketbox.api.module.auth.User;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
}