package com.ticketbox.api.user.repositories;

import com.ticketbox.api.user.domain.entities.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

}





