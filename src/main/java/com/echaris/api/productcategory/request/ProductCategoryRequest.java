package com.echaris.api.productcategory.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProductCategoryRequest(
        @NotBlank
        @Size(max = 100)
        String name,

        @NotNull
        Boolean active
) {
}