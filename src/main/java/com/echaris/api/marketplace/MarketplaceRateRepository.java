package com.echaris.api.marketplace;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MarketplaceRateRepository extends MongoRepository<MarketplaceRate, String> {
}
