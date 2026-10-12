package com.ticketbox.api.module.auth.domain.mappers;

import com.ticketbox.api.module.auth.domain.dtos.UserResponse;
import com.ticketbox.api.module.auth.domain.entities.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toUserResponse(User user);
}
