package com.echaris.api.productcategory;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ProductCategoryRepository
        extends MongoRepository<ProductCategory, String> {

    List<ProductCategory> findAllByActiveTrueOrderByNameAsc();

    List<ProductCategory> findAllByOrderByNameAsc();

    boolean existsByIdAndActiveTrue(String id);
}