package com.echaris.api.productcategory;

import com.echaris.api.admin.AdminService;
import com.echaris.api.productcategory.request.ProductCategoryRequest;
import com.echaris.api.utils.exception.ResourceExistsException;
import com.echaris.api.utils.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductCategoryService {

    private final ProductCategoryRepository categoryRepository;
    private final MongoTemplate mongoTemplate;
    private final AdminService adminService;

    @PostConstruct
    public void initialize() {
        mongoTemplate.indexOps(ProductCategory.class)
                .createIndex(new Index().on("nameKey", Sort.Direction.ASC).unique());
    }

    public List<ProductCategory> available() {
        return categoryRepository.findAllByActiveTrueOrderByNameAsc();
    }

    public boolean isAvailable(String categoryId) {
        return categoryRepository.existsByIdAndActiveTrue(categoryId);
    }

    public boolean exists(String categoryId) {
        return categoryRepository.existsById(categoryId);
    }

    public List<ProductCategory> listAll() {
        adminService.getCurrentAdminProfile();

        return categoryRepository.findAllByOrderByNameAsc();
    }

    public ProductCategory getAvailable(String categoryId) {
        ProductCategory category = find(categoryId);

        if (!category.isActive()) {
            throw new ResourceNotFoundException("Product category not found");
        }

        return category;
    }

    public ProductCategory get(String categoryId) {
        adminService.getCurrentAdminProfile();

        return find(categoryId);
    }

    public ProductCategory create(ProductCategoryRequest request) {
        adminService.getCurrentAdminProfile();

        ProductCategory category = new ProductCategory();
        category.setId(UUID.randomUUID().toString());

        return save(category, request);
    }

    public ProductCategory update(
            String categoryId,
            ProductCategoryRequest request
    ) {
        adminService.getCurrentAdminProfile();

        return save(find(categoryId), request);
    }

    public void remove(String categoryId) {
        adminService.getCurrentAdminProfile();

        ProductCategory category = find(categoryId);

        if (category.isActive()) {
            category.setActive(false);
            category.setUpdatedAt(LocalDateTime.now());
            categoryRepository.save(category);
        }
    }

    private ProductCategory find(String categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product category not found"
                ));
    }

    private ProductCategory save(
            ProductCategory category,
            ProductCategoryRequest request
    ) {
        String name = request.name().strip();
        category.setName(name);
        category.setNameKey(name.toLowerCase(Locale.ROOT));
        category.setActive(request.active());
        category.setUpdatedAt(LocalDateTime.now());

        try {
            return categoryRepository.save(category);

        } catch (DuplicateKeyException exception) {
            throw new ResourceExistsException(
                    "A product category with this name already exists"
            );
        }
    }
}