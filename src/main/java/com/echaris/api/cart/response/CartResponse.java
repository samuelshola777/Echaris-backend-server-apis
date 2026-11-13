package com.echaris.api.cart.response;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        String id,
        String userId,
        List<CartItemResponse> items,
        BigDecimal subtotal
) {
}