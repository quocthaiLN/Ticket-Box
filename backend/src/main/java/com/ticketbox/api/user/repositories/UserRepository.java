package com.ticketbox.api.user.repositories;

import com.ticketbox.api.user.domain.entities.User;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    
}