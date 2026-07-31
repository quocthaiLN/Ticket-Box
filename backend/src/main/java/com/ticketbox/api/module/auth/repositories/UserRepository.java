package com.ticketbox.api.module.auth.repositories;

import com.ticketbox.api.module.auth.domain.entities.User;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
}