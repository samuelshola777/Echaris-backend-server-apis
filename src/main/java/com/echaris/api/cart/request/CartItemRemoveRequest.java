package com.echaris.api.cart.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CartItemRemoveRequest(
        @NotBlank
        @Size(max = 128)
        String productId
) {
}