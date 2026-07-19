package com.ticketbox.api.user.services;

import com.ticketbox.api.user.domain.dtos.request.CreateUserRequest;
import com.ticketbox.api.user.domain.entities.User;

public interface UserService {
    public User CreateUser(CreateUserRequest request);


}





