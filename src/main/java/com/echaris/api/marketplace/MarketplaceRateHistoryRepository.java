package com.echaris.api.marketplace;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MarketplaceRateHistoryRepository extends MongoRepository<MarketplaceRateHistory, String> {
}
