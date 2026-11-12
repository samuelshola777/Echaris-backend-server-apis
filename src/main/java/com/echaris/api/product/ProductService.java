package com.echaris.api.product;

import com.echaris.api.admin.AdminService;
import com.echaris.api.product.request.ProductRequest;
import com.echaris.api.productcategory.ProductCategory;
import com.echaris.api.productcategory.ProductCategoryService;
import com.echaris.api.utils.PaginatedData;
import com.echaris.api.utils.UtilsService;
import com.echaris.api.utils.exception.InvalidInputException;
import com.echaris.api.utils.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryService categoryService;
    private final AdminService adminService;
    private final MongoTemplate mongoTemplate;
    private final UtilsService utilsService;
    private final ProductStockService stockService;

    public PaginatedData catalog(String categoryId, String search, int page, int size) {
        String selectedCategoryId = categoryId == null ? "" : categoryId.strip();
        List<String> availableCategoryIds = categoryService.available().stream()
                .map(ProductCategory::getId)
                .filter(id -> selectedCategoryId.isEmpty() || id.equals(selectedCategoryId))
                .toList();

        Query query = Query.query(Criteria.where("active").is(true)
                .and("categoryId").in(availableCategoryIds));

        return list(query, search, page, size);
    }

    public Product catalogProduct(String productId) {
        Product product = find(productId);

        if (!product.isActive() || !categoryService.isAvailable(product.getCategoryId())) {
            throw new ResourceNotFoundException("Product not found");
        }

        return product;
    }

    public PaginatedData listAll(String categoryId, String search, int page, int size) {
        adminService.getCurrentAdminProfile();

        Query query = new Query();

        if (categoryId != null && !categoryId.isBlank()) {
            query.addCriteria(Criteria.where("categoryId").is(categoryId.strip()));
        }

        return list(query, search, page, size);
    }

    public Product get(String productId) {
        adminService.getCurrentAdminProfile();

        return find(productId);
    }

    @Transactional
    public Product create(ProductRequest request) {
        String adminUserId = adminService.getCurrentAdminProfile().userId();

        Product product = new Product();
        product.setId(UUID.randomUUID().toString());

        Product saved = save(product, request);
        stockService.recordInitialStock(saved, adminUserId);

        return saved;
    }

    @Transactional
    public Product update(String productId, ProductRequest request) {
        String adminUserId = adminService.getCurrentAdminProfile().userId();
        Product product = find(productId);
        int quantityBefore = product.getStockQuantity();

        Product saved = save(product, request);
        stockService.recordStockReplacement(saved, quantityBefore, adminUserId);

        return saved;
    }

    public void remove(String productId) {
        adminService.getCurrentAdminProfile();

        Product product = find(productId);

        if (product.isActive()) {
            product.setActive(false);
            product.setUpdatedAt(LocalDateTime.now());
            productRepository.save(product);
        }
    }

    private Product find(String productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
    }

    private Product save(Product product, ProductRequest request) {
        String categoryId = request.categoryId().strip();
        boolean validCategory = request.active()
                ? categoryService.isAvailable(categoryId)
                : categoryService.exists(categoryId);

        if (!validCategory) {
            throw new ResourceNotFoundException("Product category is unavailable");
        }

        List<String> imageUrls = request.imageUrls().stream()
                .map(this::validateImageUrl)
                .toList();

        product.setName(request.name().strip());
        product.setDescription(request.description().strip());
        product.setCategoryId(categoryId);
        product.setPrice(request.price());
        product.setImageUrls(imageUrls);
        product.setStockQuantity(request.stockQuantity());
        product.setMadeToOrder(request.madeToOrder());
        product.setActive(request.active());
        product.setUpdatedAt(LocalDateTime.now());

        return productRepository.save(product);
    }

    private String validateImageUrl(String value) {
        String url = value.strip();

        try {
            URI uri = URI.create(url);

            if (uri.getHost() != null && uri.getUserInfo() == null
                    && ("https".equalsIgnoreCase(uri.getScheme())
                    || "http".equalsIgnoreCase(uri.getScheme()))) {
                return url;
            }

        } catch (IllegalArgumentException ignored) {
            // A malformed URL receives the same validation error.
        }

        throw new InvalidInputException("Each product image must have a valid HTTP or HTTPS URL");
    }

    private PaginatedData list(Query query, String search, int page, int size) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) {
            throw new InvalidInputException("Invalid page or size");
        }

        if (search != null && !search.isBlank()) {
            String term = search.strip();

            if (term.length() > 150) {
                throw new InvalidInputException("Product search cannot exceed 150 characters");
            }

            query.addCriteria(Criteria.where("name").regex(
                    Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE)
            ));
        }

        long total = mongoTemplate.count(query, Product.class);

        List<Product> products = mongoTemplate.find(
                query.with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")))
                        .skip((long) (page - 1) * size)
                        .limit(size),
                Product.class
        );

        return utilsService.createPaginatedData(products, page, size, total);
    }
}