package com.echaris.api.product;

import com.echaris.api.admin.AdminService;

import com.echaris.api.utils.PaginatedData;
import com.echaris.api.utils.UtilsService;
import com.echaris.api.utils.exception.InvalidInputException;
import com.echaris.api.utils.exception.ResourceExistsException;
import com.echaris.api.utils.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductStockService {

    private final ProductRepository productRepository;
    private final AdminService adminService;
    private final MongoTemplate mongoTemplate;
    private final UtilsService utilsService;

    @PostConstruct
    public void initialize() {
        mongoTemplate.indexOps(ProductStockMovement.class).createIndex(
                new Index()
                        .on("productId", Sort.Direction.ASC)
                        .on("occurredAt", Sort.Direction.DESC)
                        .on("_id", Sort.Direction.DESC)
        );
    }

    @Transactional
    public ProductStockMovement adjust(String productId, int quantityChange, String requestKey) {
        String adminUserId = adminService.getCurrentAdminProfile().userId();

        if (quantityChange == 0) {
            throw new InvalidInputException("Stock adjustment must be nonzero");
        }

        if (requestKey == null || !requestKey.matches("[A-Za-z0-9_-]{8,100}")) {
            throw new InvalidInputException(
                    "Provide an Idempotency-Key of 8-100 letters, digits, hyphens or underscores"
            );
        }

        return changeStock(productId, quantityChange, adminUserId, null,
                ProductStockMovementType.ADMIN_ADJUSTMENT, requestKey);
    }

    /**
     * Called by the trusted order service during order confirmation.
     * Combine quantities for the same product within an order before calling.
     */
    @Transactional
    public ProductStockMovement recordPurchase(
            String productId, int quantity, String customerUserId, String orderId
    ) {
        if (quantity <= 0 || customerUserId == null || customerUserId.isBlank()
                || orderId == null || orderId.isBlank() || orderId.length() > 128) {
            throw new InvalidInputException(
                    "A positive quantity, customer user ID and valid order ID are required"
            );
        }

        return changeStock(productId, -quantity, customerUserId, orderId, ProductStockMovementType.PURCHASE, orderId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ProductStockMovement reserveForMarketplace(
            String productId, int quantity, String adminUserId, String financingId
    ) {
        if (quantity < 1) {
            throw new InvalidInputException("Marketplace reservation quantity must be positive");
        }
        return changeStock(productId, -quantity, adminUserId, financingId,
                ProductStockMovementType.MARKETPLACE_RESERVATION, financingId);
    }

    public PaginatedData history(String productId, int page, int size) {
        adminService.getCurrentAdminProfile();

        if (page < 1 || page > 10000 || size < 1 || size > 100) {
            throw new InvalidInputException("Invalid page or size");
        }

        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product not found");
        }

        Query query = Query.query(Criteria.where("productId").is(productId));
        long total = mongoTemplate.count(query, ProductStockMovement.class);
        List<ProductStockMovement> movements = mongoTemplate.find(
                query.with(Sort.by(Sort.Order.desc("occurredAt"), Sort.Order.desc("id")))
                        .skip((long) (page - 1) * size)
                        .limit(size),
                ProductStockMovement.class
        );

        return utilsService.createPaginatedData(movements, page, size, total);
    }

    void recordInitialStock(Product product, String adminUserId) {
        record(UUID.randomUUID().toString(), product, ProductStockMovementType.INITIAL_STOCK, 0, adminUserId, null);
    }

    void recordStockReplacement(Product product, int quantityBefore, String adminUserId) {
        if (quantityBefore != product.getStockQuantity()) {
            record(UUID.randomUUID().toString(), product, ProductStockMovementType.ADMIN_SET, quantityBefore,
                    adminUserId, null);
        }
    }

    private ProductStockMovement changeStock(
            String productId, int quantityChange, String actorUserId,
            String orderId, ProductStockMovementType movementType, String operationKey
    ) {
        // Length-prefix the product ID so the product and operation key cannot overlap.
        String movementId = movementType.name() + ":" + productId.length() + ":" + productId + ":" + operationKey;
        ProductStockMovement existing = mongoTemplate.findById(movementId, ProductStockMovement.class);

        if (existing != null) {
            if (existing.getQuantityChange() != quantityChange
                    || !actorUserId.equals(existing.getActorUserId())) {
                throw new ResourceExistsException(
                        "This stock operation was already recorded with different details"
                );
            }

            return existing;
        }

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        int quantityBefore = product.getStockQuantity();
        long quantityAfter = (long) quantityBefore + quantityChange;

        if (quantityAfter < 0 && movementType == ProductStockMovementType.MARKETPLACE_RESERVATION) {
            throw new ResourceExistsException("Requested quantity exceeds available stock");
        }
        if (quantityAfter < 0 || quantityAfter > Integer.MAX_VALUE) {
            throw new InvalidInputException("Stock adjustment would leave an invalid quantity");
        }

        product.setStockQuantity((int) quantityAfter);
        product.setUpdatedAt(LocalDateTime.now());
        Product saved = productRepository.save(product);

        return record(movementId, saved, movementType, quantityBefore, actorUserId, orderId);
    }

    private ProductStockMovement record(
            String movementId, Product product, ProductStockMovementType movementType, int quantityBefore,
            String actorUserId, String orderId
    ) {
        ProductStockMovement movement = new ProductStockMovement();
        movement.setId(movementId);
        movement.setProductId(product.getId());
        movement.setType(movementType);
        movement.setQuantityBefore(quantityBefore);
        movement.setQuantityChange(product.getStockQuantity() - quantityBefore);
        movement.setQuantityAfter(product.getStockQuantity());
        movement.setActorUserId(actorUserId);
        movement.setOrderId(orderId);
        movement.setOccurredAt(Instant.now());

        return mongoTemplate.insert(movement);
    }
}