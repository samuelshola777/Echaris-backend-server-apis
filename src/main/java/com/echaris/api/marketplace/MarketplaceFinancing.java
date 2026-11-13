package com.echaris.api.marketplace;

import com.echaris.api.utils.BaseEntity;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.mapping.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Document(collection = "marketplace_financings")
public class MarketplaceFinancing extends BaseEntity {

    static final int PRODUCT_RELEASE_PERCENT = 50;
    static final int CAPITAL_PAYOUT_PERCENT = 100;

    private String userId;
    private String productId;
    private String productName;
    private String name;
    private int quantity;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal productTotal;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal capital;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal monthlyRate;

    @Field(targetType = FieldType.DECIMAL128)
    private BigDecimal accumulatedReturns = BigDecimal.ZERO;

    private MarketplaceFinancingStatus status = MarketplaceFinancingStatus.AWAITING_PAYMENT;
    private MarketplaceFulfillment fulfillment;
    private MarketplaceFulfillmentStatus fulfillmentStatus = MarketplaceFulfillmentStatus.AWAITING_RELEASE;
    private List<MarketplaceFulfillmentHistory> fulfillmentHistory = new ArrayList<>();
    private String agreementVersion;
    private LocalDateTime agreementAcceptedAt;
    private String fundingPaymentId;
    private LocalDateTime approvedAt;
    private LocalDateTime nextReturnAt;
    private LocalDateTime releasedAt;
    private LocalDateTime maturedAt;
    private LocalDateTime capitalRequestedAt;
    private String capitalPayoutId;
    private LocalDateTime capitalPaidAt;

    @Version
    private Long version;

    boolean hasReachedReturnPercentage(int percentage) {
        BigDecimal earned = accumulatedReturns.movePointRight(2);
        BigDecimal threshold = productTotal.multiply(BigDecimal.valueOf(percentage));
        return earned.compareTo(threshold) >= 0;
    }
}