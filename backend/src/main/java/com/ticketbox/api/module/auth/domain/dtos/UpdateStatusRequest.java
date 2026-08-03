package com.ticketbox.api.module.auth.domain.dtos;

import com.ticketbox.api.module.auth.domain.entities.User;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStatusRequest {

    @NotNull(message = "Status cannot be null")
    private User.UserStatus status;
}
