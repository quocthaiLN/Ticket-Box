package com.ticketbox.api.user.services;

import org.springframework.stereotype.Service;

import com.ticketbox.api.user.domain.dtos.request.CreateUserRequest;
import com.ticketbox.api.user.domain.entities.User;
import com.ticketbox.api.user.domain.entities.UserAccount;
import com.ticketbox.api.user.repositories.UserAccountRepository;
import com.ticketbox.api.user.repositories.UserRepository;

import com.ticketbox.api.utils.PasswordEncoder;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public User CreateUser(CreateUserRequest request) {
        User user = User.builder()
                .email(request.getEmail())
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .role(User.UserRole.AUDIENCE)
                .status(User.UserStatus.PENDING)
                .build();

        User savedUser = userRepository.save(user);

        UserAccount userAccount = UserAccount.builder()
                .user(savedUser)
                .passwordHash(passwordEncoder.encode(request.getPassword())).build();
        
        userAccountRepository.save(userAccount);

        return savedUser;
    }

}





