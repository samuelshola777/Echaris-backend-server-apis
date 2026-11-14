package com.echaris.api.marketplace;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MarketplaceFinancingRepository extends MongoRepository<MarketplaceFinancing, String> {
    boolean existsByProductIdAndUserIdAndCompletedFalse(String productId, String userId);

    boolean existsByFundingPaymentId(String userPaymentId);
}
