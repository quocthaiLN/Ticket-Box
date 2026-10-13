package com.ticketbox.api.module.audit.domain.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActorDto {
    private UUID id;
    private String email;
    private String fullName;
}
