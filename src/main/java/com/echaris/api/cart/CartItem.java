package com.echaris.api.cart;

public record CartItem(
        String productId,
        int quantity
) {
}