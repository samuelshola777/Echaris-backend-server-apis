package com.echaris.api.marketplace;

import com.echaris.api.commitment.CommitmentRules;
import com.echaris.api.utils.PaginatedData;
import com.echaris.api.utils.PaginationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class MarketplaceReturnService {

    private static final int BATCH_SIZE = 100;

    private final MongoTemplate mongoTemplate;
    private final TransactionTemplate transactionTemplate;
    private final MarketplaceNotificationService notificationService;
    private final PaginationService paginationService;

    @PostConstruct
    public void createIndexes() {
        mongoTemplate.indexOps(MarketplaceReturn.class)
                .createIndex(new Index().on("financingId", Sort.Direction.ASC).on("monthNumber", Sort.Direction.ASC)
                        .unique());
    }

    void scheduleReturn(MarketplaceFinancing financing, int month) {
        MarketplaceReturn installment = new MarketplaceReturn();
        installment.setId(financing.getId() + ":" + month);
        installment.setFinancingId(financing.getId());
        installment.setMonthNumber(month);
        LocalDateTime dueAt = financing.getApprovedAt().toLocalDate().plusMonths(month).atStartOfDay();
        installment.setDueAt(dueAt.toInstant(ZoneOffset.UTC));
        installment.setAmount(CommitmentRules.monthlyReturn(financing.getCapital(), financing.getMonthlyRate()));
        mongoTemplate.insert(installment);
        financing.setNextReturnAt(dueAt);
    }

    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    public void confirmDueReturns() {
        confirmDueReturns(Instant.now());
    }

    void confirmDueReturns(Instant cutoff) {
        LocalDateTime localCutoff = LocalDateTime.ofInstant(cutoff, ZoneOffset.UTC);
        String afterId = null;
        List<MarketplaceFinancing> batch;

        do {
            batch = findDueFinancings(localCutoff, afterId);

            for (MarketplaceFinancing financing : batch) {
                afterId = financing.getId();
                confirmFinancingReturns(afterId, localCutoff);
            }
        } while (batch.size() == BATCH_SIZE);
    }

    private List<MarketplaceFinancing> findDueFinancings(LocalDateTime cutoff, String afterId) {
        Criteria due = Criteria.where("status").is(MarketplaceFinancingStatus.ACCUMULATING)
                .and("nextReturnAt").lte(cutoff);

        if (afterId != null) {
            due.and("id").gt(afterId);
        }

        Query query = Query.query(due).with(Sort.by("id")).limit(BATCH_SIZE);
        query.fields().include("id");
        return mongoTemplate.find(query, MarketplaceFinancing.class);
    }

    private void confirmFinancingReturns(String financingId, LocalDateTime cutoff) {
        try {
            boolean moreDue;
            do {
                // Commit each month separately so a failed month can be retried.
                moreDue = Boolean.TRUE.equals(transactionTemplate.execute(
                        status -> confirmNextReturn(financingId, cutoff)));
            } while (moreDue);
        } catch (RuntimeException exception) {
            log.warn("Marketplace return processing failed for financing {}", financingId, exception);
        }
    }

    private boolean confirmNextReturn(String financingId, LocalDateTime cutoff) {
        MarketplaceFinancing financing = mongoTemplate.findById(financingId, MarketplaceFinancing.class);
        if (financing == null || financing.getStatus() != MarketplaceFinancingStatus.ACCUMULATING
                || financing.getNextReturnAt() == null || financing.getNextReturnAt().isAfter(cutoff)) {
            return false;
        }

        Query due = Query.query(Criteria.where("financingId").is(financingId)
                .and("status").is(MarketplaceReturnStatus.PENDING)
                .and("dueAt").is(financing.getNextReturnAt().toInstant(ZoneOffset.UTC)));
        MarketplaceReturn installment = mongoTemplate.findOne(due, MarketplaceReturn.class);
        if (installment == null) {
            throw new IllegalStateException("Scheduled marketplace return is missing");
        }

        Instant confirmedAt = Instant.now();
        LocalDateTime localConfirmedAt = LocalDateTime.ofInstant(confirmedAt, ZoneOffset.UTC);
        installment.setStatus(MarketplaceReturnStatus.CONFIRMED);
        installment.setConfirmedAt(confirmedAt);
        installment.setUpdatedAt(LocalDateTime.now());
        mongoTemplate.save(installment);

        financing.setAccumulatedReturns(financing.getAccumulatedReturns().add(installment.getAmount()));
        boolean releaseProduct = financing.getReleasedAt() == null
                && financing.hasReachedReturnPercentage(MarketplaceFinancing.PRODUCT_RELEASE_PERCENT);
        boolean mature = financing.hasReachedReturnPercentage(MarketplaceFinancing.CAPITAL_PAYOUT_PERCENT);

        if (releaseProduct) {
            financing.setReleasedAt(localConfirmedAt);
            financing.setFulfillmentStatus(MarketplaceFulfillmentStatus.READY_FOR_FULFILLMENT);
        }
        if (mature) {
            financing.setStatus(MarketplaceFinancingStatus.MATURED);
            financing.setMaturedAt(localConfirmedAt);
            financing.setNextReturnAt(null);
        } else {
            scheduleReturn(financing, installment.getMonthNumber() + 1);
        }
        financing.setUpdatedAt(LocalDateTime.now());
        mongoTemplate.save(financing);

        if (releaseProduct) {
            notificationService.sendProductRelease(financing);
        }
        if (mature) {
            notificationService.sendFinancingMatured(financing);
        }
        return financing.getNextReturnAt() != null && !financing.getNextReturnAt().isAfter(cutoff);
    }

    PaginatedData getReturns(String financingId, int page, int size) {
        Query filter = Query.query(Criteria.where("financingId").is(financingId));
        return paginationService.paginate(filter, MarketplaceReturn.class, page, size,
                "monthNumber", "ASC", Set.of("monthNumber"));
    }
}