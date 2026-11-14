package com.echaris.api.marketplace;

import com.echaris.api.admin.AdminService;
import com.echaris.api.marketplace.request.MarketplaceRateRequest;
import com.echaris.api.utils.PaginatedData;
import com.echaris.api.utils.PaginationService;
import com.echaris.api.utils.exception.InvalidOperationException;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MarketplaceRateService {

    private static final String RATE_ID = "current";
    private static final BigDecimal DEFAULT_PERCENTAGE = new BigDecimal("10.00");

    private final MarketplaceRateRepository marketplaceRateRepository;
    private final MarketplaceRateHistoryRepository marketplaceRateHistoryRepository;
    private final AdminService adminService;
    private final PaginationService paginationService;

    @EventListener(ApplicationReadyEvent.class)
    public void seedDefaultRate() {
        if (marketplaceRateRepository.existsById(RATE_ID)) {
            return;
        }
        MarketplaceRate rate = new MarketplaceRate();
        rate.setId(RATE_ID);
        rate.setPercentage(DEFAULT_PERCENTAGE);

        try {
            marketplaceRateRepository.save(rate);
        } catch (DuplicateKeyException ignored) {
            // Another instance already seeded the rate.
        }
    }

    public MarketplaceRate getRate() {
        adminService.getCurrentAdminProfile();
        return requireRate();
    }

    BigDecimal getConfiguredPercentage() {
        return requireRate().getPercentage();
    }

    @Transactional
    public MarketplaceRate updateRate(MarketplaceRateRequest request) {
        String adminId = adminService.getCurrentAdminProfile().userId();
        MarketplaceRate rate = requireRate();
        if (!Objects.equals(rate.getVersion(), request.version())) {
            throw new OptimisticLockingFailureException("Marketplace rate changed. Reload before updating");
        }
        if (rate.getPercentage().compareTo(request.percentage()) == 0) {
            return rate;
        }
        MarketplaceRateHistory history = new MarketplaceRateHistory();
        history.setPreviousPercentage(rate.getPercentage());
        history.setNewPercentage(request.percentage());
        history.setChangedBy(adminId);
        history.setChangedAt(LocalDateTime.now());
        rate.setPercentage(request.percentage());
        rate.setUpdatedBy(adminId);
        rate.setUpdatedAt(LocalDateTime.now());
        MarketplaceRate saved = marketplaceRateRepository.save(rate);
        marketplaceRateHistoryRepository.save(history);
        return saved;
    }

    public PaginatedData getRateHistory(int page, int size) {
        adminService.getCurrentAdminProfile();
        return paginationService.paginate(new Query(), MarketplaceRateHistory.class, page, size,
                "changedAt", "DESC", Set.of("changedAt"));
    }

    private MarketplaceRate requireRate() {
        return marketplaceRateRepository.findById(RATE_ID)
                .orElseThrow(() -> new InvalidOperationException("The marketplace rate has not been configured"));
    }
}