package com.echaris.api.utils;

import lombok.Builder;

@Builder
public record PaginatedData(
        int totalPage,
        int numberOfElements,
        long totalElements,
        Object data
) {
        public static PaginatedData empty(String message) {
                return PaginatedData.builder()
                    .numberOfElements(0)
                    .totalElements(0)
                    .totalPage(0)
                    .data(message)  // or .data(Collections.emptyList()) if you want to maintain List type
                    .build();
            }
}
