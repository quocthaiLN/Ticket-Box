package com.ticketbox.api.infrastructure.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private ErrorBody error;
    private Map<String, Object> meta;

    public static ErrorResponse of(String code, String message, Object details) {
        return ErrorResponse.builder()
                .error(ErrorBody.builder()
                        .code(code)
                        .message(message)
                        .details(details)
                        .build())
                .meta(Map.of("request_id", "req_" + UUID.randomUUID().toString().substring(0, 8)))
                .build();
    }

    public static ErrorResponse of(String code, String message) {
        return of(code, message, null);
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorBody {
        private String code;
        private String message;
        private Object details;
    }
}
