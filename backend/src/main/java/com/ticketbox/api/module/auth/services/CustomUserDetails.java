package com.ticketbox.api.module.auth.services;

import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserProvider;
import com.ticketbox.api.module.auth.domain.entities.UserStatus;
import com.ticketbox.api.module.auth.domain.entities.UserAccount;
import com.ticketbox.api.module.auth.domain.entities.UserAccountStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class CustomUserDetails implements UserDetails {

    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        String roleName = user.getRole().name();
        return List.of(
                new SimpleGrantedAuthority(roleName),
                new SimpleGrantedAuthority("ROLE_" + roleName));
    }

    @Override
    public String getPassword() {
        if (user.getAccounts() == null) {
            return "";
        }
        return user.getAccounts().stream()
                .filter(acc -> acc.getProvider().equals(UserProvider.LOCAL)
                        && acc.getUserAccountStatus() == UserAccountStatus.ACTIVE
                        && acc.getDeletedAt() == null)
                .map(UserAccount::getPasswordHash)
                .findFirst()
                .orElse("");
    }

    @Override
    public String getUsername() {
        return user.getId().toString();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.getStatus() != UserStatus.SUSPENDED && user.getStatus() != UserStatus.DELETED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() == UserStatus.ACTIVE && user.getDeletedAt() == null;
    }
}
