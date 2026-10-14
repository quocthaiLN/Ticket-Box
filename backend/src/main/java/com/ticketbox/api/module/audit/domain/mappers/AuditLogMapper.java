package com.ticketbox.api.module.audit.domain.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketbox.api.module.audit.domain.dtos.ActorDto;
import com.ticketbox.api.module.audit.domain.dtos.AuditLogDetailResponse;
import com.ticketbox.api.module.audit.domain.dtos.AuditLogResponse;
import com.ticketbox.api.module.audit.domain.entities.AuditLog;
import com.ticketbox.api.module.auth.domain.entities.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class AuditLogMapper {

    @Autowired
    protected ObjectMapper objectMapper;

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public abstract AuditLogResponse toResponse(AuditLog auditLog);

    @Mapping(target = "metadata", source = "metadata", qualifiedByName = "deserializeMetadata")
    public abstract AuditLogDetailResponse toDetailResponse(AuditLog auditLog);

    public abstract ActorDto toActorDto(User actor);

    @Named("deserializeMetadata")
    protected Object deserializeMetadata(String metadata) {
        if (metadata == null || metadata.isBlank()) {
            return null;
        }
        if (objectMapper == null) {
            return metadata;
        }
        try {
            return objectMapper.readTree(metadata);
        } catch (Exception e) {
            return metadata;
        }
    }
}
