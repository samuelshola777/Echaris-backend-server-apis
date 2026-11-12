package com.echaris.api.product;

import com.echaris.api.product.request.ProductStockAdjustmentRequest;
import com.echaris.api.utils.ApiResponse;
import com.echaris.api.utils.PaginatedData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/product/private/{productId}/stock")
@RequiredArgsConstructor
public class ProductStockController {

    private final ProductStockService stockService;

    @PatchMapping
    public ApiResponse<ProductStockMovement> adjust(
            @PathVariable String productId,
            @RequestHeader("Idempotency-Key") String requestKey,
            @Valid @RequestBody ProductStockAdjustmentRequest request
    ) {
        return new ApiResponse<>(
                true,
                "Stock adjustment recorded",
                stockService.adjust(productId, request.quantityChange().intValueExact(), requestKey)
        );
    }

    @GetMapping("/history")
    public ApiResponse<PaginatedData> history(
            @PathVariable String productId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(
                true,
                "Product stock history retrieved",
                stockService.history(productId, page, size)
        );
    }
}