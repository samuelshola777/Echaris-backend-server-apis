package com.echaris.api.product;

import com.echaris.api.utils.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@Document(collection = "product_stock_movements")
public class ProductStockMovement extends BaseEntity {

    private String productId;
    private ProductStockMovementType type;
    private int quantityBefore;
    private int quantityChange;
    private int quantityAfter;
    private String actorUserId;
    private String orderId;
    private Instant occurredAt;
}