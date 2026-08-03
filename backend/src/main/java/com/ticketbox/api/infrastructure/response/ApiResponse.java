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
public class ApiResponse<T> {

    private T data;

    private PaginationMeta pagination;

    private Map<String, Object> meta;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .data(data)
                .meta(Map.of("request_id", "req_" + UUID.randomUUID().toString().substring(0, 8)))
                .build();
    }

    public static <T> ApiResponse<T> success(T data, PaginationMeta pagination) {
        return ApiResponse.<T>builder()
                .data(data)
                .pagination(pagination)
                .meta(Map.of("request_id", "req_" + UUID.randomUUID().toString().substring(0, 8)))
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PaginationMeta {
        private Integer page;
        
        @JsonProperty("page_size")
        private Integer pageSize;

        @JsonProperty("total_items")
        private Long totalItems;

        @JsonProperty("total_pages")
        private Integer totalPages;

        @JsonProperty("has_more")
        private Boolean hasMore;
    }
}
