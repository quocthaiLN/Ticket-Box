package com.ticketbox.api.module.auth.services;

import com.ticketbox.api.module.auth.domain.dtos.CreateUserRequest;
import com.ticketbox.api.module.auth.domain.entities.User;

public interface UserService {
    public User CreateUser(CreateUserRequest request);
}
