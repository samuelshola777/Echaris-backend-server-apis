package com.echaris.api.product;

import com.echaris.api.admin.AdminService;
import com.echaris.api.product.request.ProductFilterRequest;
import com.echaris.api.product.request.ProductRequest;
import com.echaris.api.productcategory.ProductCategory;
import com.echaris.api.productcategory.ProductCategoryService;
import com.echaris.api.utils.PaginatedData;
import com.echaris.api.utils.UtilsService;
import com.echaris.api.utils.exception.InvalidInputException;
import com.echaris.api.utils.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.bson.types.Decimal128;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
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
        return list(Query.query(catalogCriteria(categoryId)), search, page, size);
    }

    public List<Product> catalogProducts(Collection<String> productIds) {
        if (productIds.isEmpty()) {
            return List.of();
        }

        return mongoTemplate.find(Query.query(catalogCriteria(null).and("id").in(productIds)), Product.class);
    }

    private Criteria catalogCriteria(String categoryId) {
        String selectedCategoryId = categoryId == null ? "" : categoryId.strip();
        List<String> availableCategoryIds = categoryService.available().stream()
                .map(ProductCategory::getId)
                .filter(id -> selectedCategoryId.isEmpty() || id.equals(selectedCategoryId))
                .toList();

        return Criteria.where("active").is(true).and("categoryId").in(availableCategoryIds);
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

    public PaginatedData filterCatalog(ProductFilterRequest request) {
        return filterProducts(request, true);
    }

    public PaginatedData filter(ProductFilterRequest request) {
        adminService.getCurrentAdminProfile();
        return filterProducts(request, false);
    }

    private PaginatedData filterProducts(ProductFilterRequest request, boolean catalog) {
        Query query = catalog ? Query.query(catalogCriteria(null)) : new Query();
        if (request == null) {
            return list(query, null, 1, 20);
        }

        List<Criteria> filters = new ArrayList<>();
        if (hasText(request.productId())) {
            filters.add(Criteria.where("id").is(request.productId().strip()));
        }
        if (hasText(request.categoryId())) {
            filters.add(Criteria.where("categoryId").is(request.categoryId().strip()));
        }
        if (hasText(request.name())) {
            filters.add(Criteria.where("name").regex(containsText(request.name())));
        }
        if (hasText(request.description())) {
            filters.add(Criteria.where("description").regex(containsText(request.description())));
        }
        if (request.imageUrls() != null && !request.imageUrls().isEmpty()) {
            filters.add(Criteria.where("imageUrls").all(request.imageUrls().stream().map(String::strip).toList()));
        }
        if (request.madeToOrder() != null) {
            filters.add(Criteria.where("madeToOrder").is(request.madeToOrder()));
        }
        if (request.active() != null) {
            filters.add(Criteria.where("active").is(request.active()));
        }
        // Match the BSON type used by the stored product price.
        addNumericFilters(filters, "price", decimalPrice(request.price()),
                decimalPrice(request.minPrice()), decimalPrice(request.maxPrice()));
        addNumericFilters(filters, "stockQuantity", request.stockQuantity(),
                request.minStockQuantity(), request.maxStockQuantity());

        // Keep request predicates separate from the catalog's mandatory visibility predicates.
        if (!filters.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(filters));
        }
        int page = request.page() == null ? 1 : request.page();
        int size = request.size() == null ? 20 : request.size();
        return list(query, request.search(), page, size);
    }

    private <T extends Comparable<T>> void addNumericFilters(
            List<Criteria> filters, String field, T exact, T min, T max
    ) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new InvalidInputException("Minimum " + field + " must not exceed its maximum");
        }
        if (exact != null) {
            filters.add(Criteria.where(field).is(exact));
        }
        if (min != null || max != null) {
            Criteria range = Criteria.where(field);
            if (min != null) {
                range.gte(min);
            }
            if (max != null) {
                range.lte(max);
            }
            filters.add(range);
        }
    }

    private Decimal128 decimalPrice(BigDecimal price) {
        return price == null ? null : new Decimal128(price);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Pattern containsText(String value) {
        return Pattern.compile(Pattern.quote(value.strip()), Pattern.CASE_INSENSITIVE);
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

            query.addCriteria(Criteria.where("name").regex(containsText(term)));
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