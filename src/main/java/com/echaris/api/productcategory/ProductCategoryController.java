package com.echaris.api.productcategory;

import com.echaris.api.productcategory.request.ProductCategoryRequest;
import com.echaris.api.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/product")
@RequiredArgsConstructor
public class ProductCategoryController {

    private final ProductCategoryService categoryService;

    @GetMapping("/private/catalog/categories")
    public ApiResponse<List<ProductCategory>> available() {
        return new ApiResponse<>(
                true, "Available product categories retrieved", categoryService.available()
        );
    }

    @GetMapping("/private/categories")
    public ApiResponse<List<ProductCategory>> listAll() {
        return new ApiResponse<>(
                true, "Product categories retrieved", categoryService.listAll()
        );
    }

    @GetMapping("/private/categories/{categoryId}")
    public ApiResponse<ProductCategory> getAvailable(@PathVariable String categoryId) {
        return new ApiResponse<>(
                true, "Product category retrieved", categoryService.getAvailable(categoryId)
        );
    }

    @GetMapping("/private/admin/categories/{categoryId}")
    public ApiResponse<ProductCategory> get(@PathVariable String categoryId) {
        return new ApiResponse<>(
                true, "Product category retrieved", categoryService.get(categoryId)
        );
    }

    @PostMapping("/private/categories")
    public ApiResponse<ProductCategory> create(
            @Valid @RequestBody ProductCategoryRequest request
    ) {
        return new ApiResponse<>(
                true, "Product category created", categoryService.create(request)
        );
    }

    @PutMapping("/private/categories/{categoryId}")
    public ApiResponse<ProductCategory> update(
            @PathVariable String categoryId,
            @Valid @RequestBody ProductCategoryRequest request
    ) {
        return new ApiResponse<>(
                true, "Product category updated", categoryService.update(categoryId, request)
        );
    }

    @DeleteMapping("/private/categories/{categoryId}")
    public ApiResponse<Void> remove(@PathVariable String categoryId) {
        categoryService.remove(categoryId);

        return new ApiResponse<>(true, "Product category removed", null);
    }
}