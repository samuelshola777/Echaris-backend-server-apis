package com.echaris.api.marketplace.response;

import com.echaris.api.marketplace.MarketplaceFinancing;
import com.echaris.api.product.Product;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import java.math.BigDecimal;

public record MarketplaceFinancingResponse(
        @JsonUnwrapped MarketplaceFinancing financing,
        String productName,
        BigDecimal productPrice
) {
    public static MarketplaceFinancingResponse from(MarketplaceFinancing financing, Product product) {
        return new MarketplaceFinancingResponse(
                financing,
                product == null ? null : product.getName(),
                product == null ? null : product.getPrice()
        );
    }
}