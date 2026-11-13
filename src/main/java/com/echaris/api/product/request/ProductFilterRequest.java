package com.echaris.api.product.request;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record ProductFilterRequest(
        @Size(max = 128) String productId,
        @Size(max = 64) String categoryId,
        @Size(max = 150) String search,
        @Size(max = 150) String name,
        @Size(max = 4000) String description,
        @PositiveOrZero @Digits(integer = 15, fraction = 2) BigDecimal price,
        @PositiveOrZero @Digits(integer = 15, fraction = 2) BigDecimal minPrice,
        @PositiveOrZero @Digits(integer = 15, fraction = 2) BigDecimal maxPrice,
        @Size(max = 10) List<@NotBlank @Size(max = 2048) String> imageUrls,
        @PositiveOrZero Integer stockQuantity,
        @PositiveOrZero Integer minStockQuantity,
        @PositiveOrZero Integer maxStockQuantity,
        Boolean madeToOrder,
        Boolean active,
        @Min(1) @Max(10000) Integer page,
        @Min(1) @Max(100) Integer size
) {
}