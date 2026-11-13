package com.echaris.api.product;

import com.echaris.api.product.request.ProductFilterRequest;
import com.echaris.api.product.request.ProductRequest;
import com.echaris.api.utils.ApiResponse;
import com.echaris.api.utils.PaginatedData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/product")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/private/catalog")
    public ApiResponse<PaginatedData> catalog(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(
                true, "Available products retrieved",
                productService.catalog(categoryId, search, page, size)
        );
    }

    @PostMapping("/private/catalog/filter")
    public ApiResponse<PaginatedData> filterCatalog(
            @Valid @RequestBody(required = false) ProductFilterRequest request
    ) {
        return new ApiResponse<>(true, "Available products retrieved", productService.filterCatalog(request));
    }

    @GetMapping("/private/catalog/{productId}")
    public ApiResponse<Product> catalogProduct(@PathVariable String productId) {
        return new ApiResponse<>(
                true, "Product retrieved", productService.catalogProduct(productId)
        );
    }

    @GetMapping("/private")
    public ApiResponse<PaginatedData> listAll(
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(
                true, "Products retrieved",
                productService.listAll(categoryId, search, page, size)
        );
    }

    @PostMapping("/private/filter")
    public ApiResponse<PaginatedData> filter(
            @Valid @RequestBody(required = false) ProductFilterRequest request
    ) {
        return new ApiResponse<>(true, "Products retrieved", productService.filter(request));
    }

    @GetMapping("/private/{productId}")
    public ApiResponse<Product> get(@PathVariable String productId) {
        return new ApiResponse<>(true, "Product retrieved", productService.get(productId));
    }

    @PostMapping("/private")
    public ApiResponse<Product> create(@Valid @RequestBody ProductRequest request) {
        return new ApiResponse<>(true, "Product created", productService.create(request));
    }

    @PutMapping("/private/{productId}")
    public ApiResponse<Product> update(
            @PathVariable String productId,
            @Valid @RequestBody ProductRequest request
    ) {
        return new ApiResponse<>(
                true, "Product updated", productService.update(productId, request)
        );
    }

    @DeleteMapping("/private/{productId}")
    public ApiResponse<Void> remove(@PathVariable String productId) {
        productService.remove(productId);

        return new ApiResponse<>(true, "Product removed", null);
    }
}