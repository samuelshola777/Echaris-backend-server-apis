package com.echaris.api.marketplace;

import com.echaris.api.admin.AdminService;
import com.echaris.api.marketplace.request.MarketplaceRateRequest;
import com.echaris.api.utils.PaginatedData;
import com.echaris.api.utils.PaginationService;
import com.echaris.api.utils.exception.InvalidOperationException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketplaceRateService {

    private static final String RATE_ID = "current";

    private final MongoTemplate mongoTemplate;
    private final AdminService adminService;
    private final PaginationService paginationService;

    public MarketplaceRate getRate() {
        adminService.getCurrentAdminProfile();
        return findRate();
    }

    private MarketplaceRate findRate() {
        return mongoTemplate.findById(RATE_ID, MarketplaceRate.class);
    }

    BigDecimal getConfiguredPercentage() {
        MarketplaceRate rate = findRate();

        if (rate == null) {
            throw new InvalidOperationException("The marketplace rate has not been configured");
        }
        return rate.getPercentage();
    }

    @Transactional
    public MarketplaceRate updateRate(MarketplaceRateRequest request) {
        String adminId = adminService.getCurrentAdminProfile().userId();
        MarketplaceRate rate = findRate();
        Long version = rate == null ? null : rate.getVersion();

        if (!Objects.equals(version, request.version())) {
            throw new OptimisticLockingFailureException("Marketplace rate changed. Reload before updating");
        }
        if (rate != null && rate.getPercentage().compareTo(request.percentage()) == 0) {
            return rate;
        }

        MarketplaceRateHistory history = new MarketplaceRateHistory();
        history.setId(UUID.randomUUID().toString());
        history.setPreviousPercentage(rate == null ? null : rate.getPercentage());
        history.setNewPercentage(request.percentage());
        history.setChangedBy(adminId);
        history.setChangedAt(Instant.now());

        boolean firstRate = rate == null;
        if (firstRate) {
            rate = new MarketplaceRate();
            rate.setId(RATE_ID);
        }
        rate.setPercentage(request.percentage());
        rate.setUpdatedBy(adminId);
        rate.setUpdatedAt(LocalDateTime.now());

        try {
            MarketplaceRate saved = firstRate ? mongoTemplate.insert(rate) : mongoTemplate.save(rate);
            mongoTemplate.insert(history);
            return saved;
        } catch (DuplicateKeyException exception) {
            throw new OptimisticLockingFailureException("Marketplace rate changed. Reload before updating", exception);
        }
    }

    public PaginatedData getRateHistory(int page, int size) {
        adminService.getCurrentAdminProfile();
        return paginationService.paginate(new Query(), MarketplaceRateHistory.class, page, size,
                "changedAt", "DESC", Set.of("changedAt"));
    }
}