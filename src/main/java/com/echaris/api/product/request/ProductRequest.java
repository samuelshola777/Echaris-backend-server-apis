package com.echaris.api.product.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ProductRequest(
        @NotBlank
        @Size(max = 150)
        String name,

        @NotBlank
        @Size(max = 4000)
        String description,

        @NotBlank
        @Size(max = 64)
        String categoryId,

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 15, fraction = 2)
        BigDecimal price,

        @NotNull
        @Size(max = 10)
        List<@NotBlank @Size(max = 2048) String> imageUrls,

        @NotNull
        @PositiveOrZero
        Integer stockQuantity,

        @NotNull
        Boolean madeToOrder,

        @NotNull
        Boolean active
) {
}