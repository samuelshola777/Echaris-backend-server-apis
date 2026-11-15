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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
        mongoTemplate.indexOps(MarketplaceReturn.class).createIndex(
                new Index()
                        .on("financingId", Sort.Direction.ASC)
                        .on("monthNumber", Sort.Direction.ASC)
                        .unique()
        );
    }

    void scheduleReturn(MarketplaceFinancing financing, int monthNumber) {
        LocalDateTime dueAt = financing.getApprovedAt().toLocalDate().plusMonths(monthNumber).atStartOfDay();

        MarketplaceReturn installment = new MarketplaceReturn();
        installment.setId(financing.getId() + ":" + monthNumber);
        installment.setFinancingId(financing.getId());
        installment.setMonthNumber(monthNumber);
        installment.setDueAt(dueAt.toInstant(ZoneOffset.UTC));
        installment.setAmount(CommitmentRules.monthlyReturn(financing.getCapital(), financing.getMonthlyRate()));

        mongoTemplate.insert(installment);
        financing.setNextReturnAt(dueAt);

        logAfterCommit(() -> log.info(
                "Scheduled marketplace return: financingId={}, monthNumber={}, dueAt={}",
                financing.getId(), monthNumber, installment.getDueAt()
        ));
    }

    @Scheduled(cron = "0 45 0 * * *", zone = "Africa/Lagos")
    public void confirmDueReturns() {
        confirmDueReturns(Instant.now());
    }

    void confirmDueReturns(Instant cutoff) {
        LocalDateTime dueBy = LocalDateTime.ofInstant(cutoff, ZoneOffset.UTC);
        String lastFinancingId = null;
        List<MarketplaceFinancing> batch;
        long candidates = 0;

        log.info("Checking due marketplace returns: cutoff={}", cutoff);
        do {
            batch = findFinancingsWithDueReturns(dueBy, lastFinancingId);
            candidates += batch.size();
            log.info("Fetched marketplace return candidates: batchSize={}, afterFinancingId={}",
                    batch.size(), lastFinancingId);

            for (MarketplaceFinancing financing : batch) {
                lastFinancingId = financing.getId();
                confirmReturnInTransaction(lastFinancingId, dueBy);
            }
        } while (batch.size() == BATCH_SIZE);
        log.info("Finished checking marketplace returns: candidates={}", candidates);
    }

    PaginatedData getReturns(String financingId, int page, int size) {
        Query query = Query.query(Criteria.where("financingId").is(financingId));
        return paginationService.paginate(
                query, MarketplaceReturn.class, page, size, "monthNumber", "ASC", Set.of("monthNumber")
        );
    }

    private List<MarketplaceFinancing> findFinancingsWithDueReturns(LocalDateTime dueBy, String lastFinancingId) {
        Criteria criteria = Criteria.where("status").is(MarketplaceFinancingStatus.ACCUMULATING)
                .and("nextReturnAt").lte(dueBy);

        if (lastFinancingId != null) {
            criteria.and("id").gt(lastFinancingId);
        }

        Query query = Query.query(criteria).with(Sort.by("id")).limit(BATCH_SIZE);
        query.fields().include("id");
        return mongoTemplate.find(query, MarketplaceFinancing.class);
    }

    private void confirmReturnInTransaction(String financingId, LocalDateTime dueBy) {
        log.info("Processing due marketplace return: financingId={}", financingId);
        try {
            transactionTemplate.executeWithoutResult(status -> confirmReturnAndMatureFinancing(financingId, dueBy));
        } catch (RuntimeException exception) {
            log.warn("Marketplace return processing failed for financing {}", financingId, exception);
        }
    }

    private void confirmReturnAndMatureFinancing(String financingId, LocalDateTime dueBy) {
        MarketplaceFinancing financing = mongoTemplate.findById(financingId, MarketplaceFinancing.class);
        if (financing == null) {
            log.info("Skipped marketplace return: financingId={}, reason=financing missing", financingId);
            return;
        }
        if (financing.getStatus() != MarketplaceFinancingStatus.ACCUMULATING
                || financing.getNextReturnAt() == null || financing.getNextReturnAt().isAfter(dueBy)) {
            log.info("Skipped marketplace return: financingId={}, status={}, nextReturnAtUtc={}, cutoffUtc={}",
                    financingId, financing.getStatus(), financing.getNextReturnAt(), dueBy);
            return;
        }

        Query query = Query.query(Criteria.where("financingId").is(financingId)
                .and("status").is(MarketplaceReturnStatus.PENDING)
                .and("dueAt").is(financing.getNextReturnAt().toInstant(ZoneOffset.UTC)));
        MarketplaceReturn installment = mongoTemplate.findOne(query, MarketplaceReturn.class);
        if (installment == null) {
            throw new IllegalStateException("Scheduled marketplace return is missing");
        }

        Instant confirmedAt = Instant.now();
        LocalDateTime confirmedAtUtc = LocalDateTime.ofInstant(confirmedAt, ZoneOffset.UTC);

        installment.setStatus(MarketplaceReturnStatus.CONFIRMED);
        installment.setConfirmedAt(confirmedAt);
        installment.setUpdatedAt(LocalDateTime.now());
        mongoTemplate.save(installment);

        boolean releaseProduct = financing.getReleasedAt() == null;
        if (releaseProduct) {
            financing.setReleasedAt(confirmedAtUtc);
            financing.setFulfillmentStatus(MarketplaceFulfillmentStatus.READY_FOR_FULFILLMENT);
        }

        financing.setAccumulatedReturns(financing.getAccumulatedReturns().add(installment.getAmount()));
        financing.setStatus(MarketplaceFinancingStatus.MATURED);
        financing.setMaturedAt(confirmedAtUtc);
        financing.setNextReturnAt(null);
        financing.setUpdatedAt(LocalDateTime.now());
        mongoTemplate.save(financing);

        if (releaseProduct) {
            log.info("Requesting marketplace product release notification: financingId={}", financingId);
            notificationService.sendProductRelease(financing);
        }
        log.info("Requesting marketplace maturity notification: financingId={}", financingId);
        notificationService.sendFinancingMatured(financing);

        logAfterCommit(() -> log.info(
                "Confirmed marketplace return: financingId={}, returnId={}, monthNumber={}, status={}, "
                        + "fulfillmentStatus={}, productReleasedNow={}, confirmedAt={}",
                financingId, installment.getId(), installment.getMonthNumber(), financing.getStatus(),
                financing.getFulfillmentStatus(), releaseProduct, confirmedAt
        ));
    }

    private void logAfterCommit(Runnable writeLog) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    writeLog.run();
                }
            });
        } else {
            writeLog.run();
        }
    }
}