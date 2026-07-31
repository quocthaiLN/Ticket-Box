package com.ticketbox.api.module.auth.repositories;

import com.ticketbox.api.module.auth.domain.entities.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

}





