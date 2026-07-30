package com.ticketbox.api.module.auth;

import com.ticketbox.api.module.auth.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

}





