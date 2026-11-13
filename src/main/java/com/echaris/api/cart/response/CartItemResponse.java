package com.echaris.api.cart.response;

import java.math.BigDecimal;

public record CartItemResponse(
        String productId,
        String name,
        String imageUrl,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal,
        boolean available
) {
}