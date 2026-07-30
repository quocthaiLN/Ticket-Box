package com.ticketbox.api.module.auth;

import org.springframework.stereotype.Service;

import com.ticketbox.api.module.auth.CreateUserRequest;
import com.ticketbox.api.module.auth.User;
import com.ticketbox.api.module.auth.UserAccount;
import com.ticketbox.api.module.auth.UserAccountRepository;
import com.ticketbox.api.module.auth.UserRepository;

import com.ticketbox.api.module.auth.PasswordEncoder;

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





