package com.echaris.api.utils;

import lombok.Builder;

@Builder
public record RefinedPagination(
        int page,
        int size
) {

}
