package com.ticketbox.api.module.auth;

import com.ticketbox.api.module.auth.CreateUserRequest;
import com.ticketbox.api.module.auth.User;

public interface UserService {
    public User CreateUser(CreateUserRequest request);


}





