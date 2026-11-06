package com.echaris.api.commitment;

import com.echaris.api.admin.AdminRepository;
import com.echaris.api.audit.AuditLog;
import com.echaris.api.audit.AuditService;
import com.echaris.api.commitment.request.CommitmentCreateRequest;
import com.echaris.api.commitment.request.WithdrawalRequest;
import com.echaris.api.commitmentduration.CommitmentDurationService;
import com.echaris.api.commitmentrate.CommitmentRateHistory;
import com.echaris.api.commitmentrate.CommitmentRateService;
import com.echaris.api.customer.CustomerRepository;
import com.echaris.api.notification.NotificationService;
import com.echaris.api.payment.PayStackService;
import com.echaris.api.payment.Payment;
import com.echaris.api.payment.PaymentStatus;
import com.echaris.api.payment.Transaction;
import com.echaris.api.payment.TransactionDirection;
import com.echaris.api.payment.TransactionPurpose;
import com.echaris.api.payment.TransactionStatus;
import com.echaris.api.userauthmgt.user.User;
import com.echaris.api.userauthmgt.user.UserRepository;
import com.echaris.api.userauthmgt.user.UserType;
import com.echaris.api.utils.PaginatedData;
import com.echaris.api.utils.UtilsService;
import com.echaris.api.utils.emailsenderservice.EmailSenderService;
import com.echaris.api.utils.exception.InvalidInputException;
import com.echaris.api.utils.exception.InvalidOperationException;
import com.echaris.api.utils.exception.ProviderPaymentNotFoundException;
import com.echaris.api.utils.exception.ResourceNotFoundException;
import com.echaris.api.utils.exception.UnauthorizedException;
import io.jsonwebtoken.Claims;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommitmentService {

    private final MongoTemplate mongoTemplate;
    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final CommitmentRateService rateService;
    private final CommitmentDurationService durationService;
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final PayStackService payStackService;
    private final EmailSenderService emailSenderService;
    private final PlatformTransactionManager transactionManager;
    private final UtilsService utilsService;
    private final HttpServletRequest httpServletRequest;

    @Value("${paystack.callback.url}")
    private String callbackUrl;

    @PostConstruct
    public void createIndexes() {
        mongoTemplate.indexOps(Commitment.class)
                .createIndex(
                        new Index()
                                .on("userId", Sort.Direction.ASC)
                                .on("createdAt", Sort.Direction.DESC)
                );

        mongoTemplate.indexOps(Commitment.class)
                .createIndex(
                        new Index()
                                .on("status", Sort.Direction.ASC)
                                .on("maturityAt", Sort.Direction.ASC)
                );

        mongoTemplate.indexOps(CommitmentReturn.class)
                .createIndex(
                        new Index()
                                .on("commitmentId", Sort.Direction.ASC)
                                .on("monthNumber", Sort.Direction.ASC)
                                .unique()
                );

        mongoTemplate.indexOps(WithdrawalItem.class)
                .createIndex(
                        new Index()
                                .on("transactionId", Sort.Direction.ASC)
                                .on("returnId", Sort.Direction.ASC)
                                .unique()
                );

        mongoTemplate.indexOps(Transaction.class)
                .createIndex(
                        new Index()
                                .on("reference", Sort.Direction.ASC)
                                .unique()
                );

        mongoTemplate.indexOps(Transaction.class)
                .createIndex(
                        new Index()
                                .on("userId", Sort.Direction.ASC)
                                .on("requestKey", Sort.Direction.ASC)
                                .unique()
                );

        mongoTemplate.indexOps(Transaction.class)
                .createIndex(
                        new Index()
                                .on("status", Sort.Direction.ASC)
                                .on("updatedAt", Sort.Direction.ASC)
                );

        mongoTemplate.indexOps(Transaction.class)
                .createIndex(
                        new Index()
                                .on("userId", Sort.Direction.ASC)
                                .on("createdAt", Sort.Direction.DESC)
                );

        mongoTemplate.indexOps(Payment.class)
                .createIndex(
                        new Index()
                                .on("transactionId", Sort.Direction.ASC)
                                .unique()
                );

        mongoTemplate.indexOps(Payment.class)
                .createIndex(
                        new Index()
                                .on("userId", Sort.Direction.ASC)
                                .on("paidAt", Sort.Direction.DESC)
                );

        mongoTemplate.indexOps(Payment.class)
                .createIndex(
                        new Index()
                                .on("paidAt", Sort.Direction.DESC)
                );

        mongoTemplate.indexOps(CommitmentRateHistory.class)
                .createIndex(
                        new Index()
                                .on("changedAt", Sort.Direction.DESC)
                );

        mongoTemplate.indexOps(AuditLog.class)
                .createIndex(
                        new Index()
                                .on("occurredAt", Sort.Direction.DESC)
                );

        mongoTemplate.indexOps(AuditLog.class)
                .createIndex(
                        new Index()
                                .on("event", Sort.Direction.ASC)
                                .on("occurredAt", Sort.Direction.DESC)
                );

        mongoTemplate.indexOps(AuditLog.class)
                .createIndex(
                        new Index()
                                .on("actorUserId", Sort.Direction.ASC)
                                .on("occurredAt", Sort.Direction.DESC)
                );

        mongoTemplate.indexOps(AuditLog.class)
                .createIndex(
                        new Index()
                                .on("subjectUserId", Sort.Direction.ASC)
                                .on("occurredAt", Sort.Direction.DESC)
                );
    }

    public User currentUser() {
        Object value = httpServletRequest.getAttribute("validatedClaims");

        if (!(value instanceof Claims claims)) {
            throw new UnauthorizedException("Authentication is required");
        }

        String userId = claims.get("userId", String.class);

        if (userId == null) {
            throw new UnauthorizedException("User identity is unavailable");
        }

        return userRepository.findById(userId)
                .filter(user ->
                        user.isActive()
                                && user.isVerified()
                                && !user.isDeleted()
                )
                .orElseThrow(() ->
                        new UnauthorizedException("User account is unavailable")
                );
    }

    public User requireAdmin() {
        User user = currentUser();

        if (user.getUserType() != UserType.ADMIN) {
            throw new UnauthorizedException("Admin access is required");
        }

        return user;
    }

    private User customerUser() {
        User user = currentUser();

        if (user.getUserType() != UserType.CUSTOMER) {
            throw new UnauthorizedException("Customer access is required");
        }

        customerRepository.findByUserIdAndDeletedFalse(user.getId())
                .filter(customer -> customer.isActive())
                .orElseThrow(() ->
                        new UnauthorizedException("Customer account is unavailable")
                );

        return user;
    }

    public String currentIp() {
        return httpServletRequest.getRemoteAddr();
    }

    public Commitment create(CommitmentCreateRequest request) {
        User user = customerUser();
        String ip = currentIp();

        CommitmentRules.kobo(request.capital());
        durationService.requireAvailable(request.durationMonths());

        return inTransaction(() -> {
            Commitment commitment = new Commitment();

            commitment.setId(UUID.randomUUID().toString());
            commitment.setCustomerId(
                    customerRepository
                            .findByUserIdAndDeletedFalse(user.getId())
                            .orElseThrow()
                            .getId()
            );

            commitment.setUserId(user.getId());
            commitment.setName(request.name().trim());
            commitment.setCapital(request.capital().setScale(2));
            commitment.setDurationMonths(request.durationMonths());
            commitment.setMonthlyRate(rateService.current().getPercentage());
            commitment.setAgreementAcceptedAt(Instant.now());

            mongoTemplate.insert(commitment);

            audit(
                    "COMMITMENT_CREATED",
                    user.getId(),
                    commitment,
                    null,
                    ip,
                    Map.of(),
                    Map.of(
                            "status", commitment.getStatus().name(),
                            "capital", commitment.getCapital().toPlainString()
                    )
            );

            notifyUsers(
                    commitment,
                    "COMMITMENT_CREATED",
                    "Commitment created",
                    "Your commitment "
                            + commitment.getName()
                            + " is awaiting payment of "
                            + money(commitment.getCapital())
                            + ".",
                    false
            );

            return commitment;
        });
    }

    public Commitment get(String id) {
        return owned(id, customerUser().getId());
    }

    public PaginatedData list(int page, int size) {
        return page(
                Commitment.class,
                Criteria.where("userId").is(customerUser().getId()),
                page,
                size
        );
    }

    public List<CommitmentReturn> returns(String id) {
        Commitment commitment = get(id);

        return mongoTemplate.find(
                Query.query(
                        Criteria.where("commitmentId").is(commitment.getId())
                ).with(Sort.by("monthNumber")),
                CommitmentReturn.class
        );
    }

    public PaginatedData adminCommitments(
            String userId,
            int page,
            int size
    ) {
        requireAdmin();

        return page(
                Commitment.class,
                userId == null
                        ? new Criteria()
                        : Criteria.where("userId").is(userId),
                page,
                size
        );
    }

    public PaginatedData history(int page, int size) {
        requireAdmin();

        return page(
                CommitmentRateHistory.class,
                new Criteria(),
                page,
                size
        );
    }

    public PaginatedData audits(
            String userId,
            String event,
            int page,
            int size
    ) {
        requireAdmin();

        Criteria criteria = new Criteria();

        if (userId != null) {
            criteria = criteria.orOperator(
                    Criteria.where("actorUserId").is(userId),
                    Criteria.where("subjectUserId").is(userId)
            );
        }

        if (event != null) {
            criteria = criteria.and("event").is(event);
        }

        return page(AuditLog.class, criteria, page, size);
    }

    public PaginatedData transactions(int page, int size) {
        return page(
                Transaction.class,
                Criteria.where("userId").is(customerUser().getId()),
                page,
                size
        );
    }

    public PaginatedData payments(int page, int size) {
        return page(
                Payment.class,
                Criteria.where("userId").is(customerUser().getId()),
                page,
                size
        );
    }

    public PaginatedData adminMoney(
            boolean payments,
            String userId,
            int page,
            int size
    ) {
        requireAdmin();

        Criteria criteria = userId == null
                ? new Criteria()
                : Criteria.where("userId").is(userId);

        return payments
                ? page(Payment.class, criteria, page, size)
                : page(Transaction.class, criteria, page, size);
    }

    public Transaction fund(
            String commitmentId,
            String requestKey
    ) {
        User user = customerUser();
        String ip = currentIp();

        key(requestKey);

        String fingerprint = fingerprint(commitmentId + ":FUND");

        Transaction transaction = inTransaction(() -> {
            Transaction existing = byKey(user.getId(), requestKey);

            if (existing != null) {
                return sameRequest(existing, fingerprint);
            }

            Commitment commitment = owned(commitmentId, user.getId());

            if (commitment.getStatus() != CommitmentStatus.AWAITING_PAYMENT) {
                throw new InvalidOperationException(
                        "This commitment cannot accept another funding payment"
                );
            }

            if (commitment.getActiveTransactionId() != null) {
                return transaction(commitment.getActiveTransactionId());
            }

            Transaction attempt = newTransaction(
                    commitment,
                    requestKey,
                    fingerprint,
                    TransactionDirection.INCOMING,
                    TransactionPurpose.CAPITAL,
                    commitment.getCapital()
            );

            commitment.setActiveTransactionId(attempt.getId());
            save(commitment);

            audit(
                    "FUNDING_INITIATED",
                    user.getId(),
                    commitment,
                    attempt,
                    ip,
                    Map.of(),
                    Map.of(
                            "amount", attempt.getAmount().toPlainString(),
                            "status", "PENDING"
                    )
            );

            return attempt;
        });

        if (transaction.getStatus() != TransactionStatus.PENDING
                || transaction.getPaymentUrl() != null) {
            return transaction;
        }

        // Reuse the same reference after an unconfirmed initialization.
        try {
            return applyProvider(
                    transaction,
                    data(
                            payStackService.verifyPayment(
                                    transaction.getReference()
                            )
                    ),
                    user.getId(),
                    ip
            );

        } catch (ProviderPaymentNotFoundException notFound) {
            try {
                Map<?, ?> response = data(
                        payStackService.initializePayment(
                                Map.of(
                                        "email", user.getEmailAddress(),
                                        "amount",
                                        CommitmentRules.kobo(transaction.getAmount()),
                                        "currency", "NGN",
                                        "reference", transaction.getReference(),
                                        "callback_url", callbackUrl,
                                        "metadata",
                                        Map.of(
                                                "commitmentId", commitmentId,
                                                "userId", user.getId()
                                        )
                                )
                        )
                );

                String url = Objects.toString(
                        response.get("authorization_url"),
                        ""
                );

                if (url.isBlank()) {
                    throw new InvalidOperationException(
                            "Payment URL is unavailable"
                    );
                }

                mongoTemplate.updateFirst(
                        Query.query(
                                Criteria.where("id")
                                        .is(transaction.getId())
                                        .and("status")
                                        .is(TransactionStatus.PENDING)
                        ),
                        new Update()
                                .set("paymentUrl", url)
                                .set("updatedAt", LocalDateTime.now()),
                        Transaction.class
                );

            } catch (RuntimeException exception) {
                log.warn(
                        "Funding initialization unconfirmed: transactionId={}, errorType={}",
                        transaction.getId(),
                        exception.getClass().getSimpleName()
                );
            }

        } catch (RuntimeException exception) {
            log.warn(
                    "Funding verification unconfirmed: transactionId={}, errorType={}",
                    transaction.getId(),
                    exception.getClass().getSimpleName()
            );
        }

        return transaction(transaction.getId());
    }

    public Transaction withdraw(
            String commitmentId,
            String requestKey,
            WithdrawalRequest request
    ) {
        User user = customerUser();
        String ip = currentIp();

        key(requestKey);
        owned(commitmentId, user.getId());

        Set<String> returnIds = request.returnIds() == null
                ? Set.of()
                : request.returnIds();

        if (request.purpose() == TransactionPurpose.CAPITAL
                && !returnIds.isEmpty()) {
            throw new InvalidInputException(
                    "Capital withdrawal cannot include interest records"
            );
        }

        if (request.purpose() == TransactionPurpose.INTEREST
                && returnIds.isEmpty()) {
            throw new InvalidInputException(
                    "Select at least one interest record"
            );
        }

        String fingerprint = fingerprint(
                commitmentId
                        + ":"
                        + request.purpose()
                        + ":"
                        + String.join(",", new TreeSet<>(returnIds))
                        + ":"
                        + request.bankCode()
                        + ":"
                        + request.accountNumber()
        );

        Transaction existing = byKey(user.getId(), requestKey);

        if (existing != null) {
            return sameRequest(existing, fingerprint);
        }

        Map<?, ?> account = data(
                payStackService.resolveAccount(
                        request.accountNumber(),
                        request.bankCode()
                )
        );

        Map<?, ?> recipient = data(
                payStackService.createRecipient(
                        Map.of(
                                "type", "nuban",
                                "name",
                                Objects.toString(account.get("account_name"), ""),
                                "account_number", request.accountNumber(),
                                "bank_code", request.bankCode(),
                                "currency", "NGN"
                        )
                )
        );

        String recipientCode = Objects.toString(
                recipient.get("recipient_code"),
                ""
        );

        if (recipientCode.isBlank()) {
            throw new InvalidOperationException(
                    "Payout recipient could not be created"
            );
        }

        PreparedWithdrawal prepared = inTransaction(() -> {
            Transaction duplicate = byKey(user.getId(), requestKey);

            if (duplicate != null) {
                return new PreparedWithdrawal(
                        sameRequest(duplicate, fingerprint),
                        false
                );
            }

            Commitment commitment = owned(commitmentId, user.getId());

            if (commitment.getStatus() == CommitmentStatus.AWAITING_PAYMENT
                    || commitment.getStatus() == CommitmentStatus.REVIEW_REQUIRED) {
                throw new InvalidOperationException(
                        "This commitment is not available for withdrawals"
                );
            }

            if (commitment.getActiveTransactionId() != null) {
                throw new InvalidOperationException(
                        "A transaction is pending. Wait for Paystack to confirm its outcome"
                );
            }

            List<CommitmentReturn> selected = List.of();
            BigDecimal amount;

            if (request.purpose() == TransactionPurpose.CAPITAL) {
                if (!CommitmentRules.matured(
                        commitment.getMaturityAt(),
                        Instant.now()
                ) || commitment.getCapitalWithdrawnAt() != null) {
                    throw new InvalidOperationException(
                            "Capital is locked until maturity or has already been withdrawn"
                    );
                }

                mature(commitment);
                amount = commitment.getCapital();

            } else {
                selected = mongoTemplate.find(
                        Query.query(
                                Criteria.where("id")
                                        .in(returnIds)
                                        .and("commitmentId")
                                        .is(commitment.getId())
                                        .and("status")
                                        .is(CommitmentReturnStatus.AVAILABLE)
                        ),
                        CommitmentReturn.class
                );

                if (selected.size() != returnIds.size()) {
                    throw new InvalidOperationException(
                            "An interest record is unavailable or belongs to another commitment"
                    );
                }

                amount = selected.stream()
                        .map(CommitmentReturn::getAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            Transaction attempt = newTransaction(
                    commitment,
                    requestKey,
                    fingerprint,
                    TransactionDirection.OUTGOING,
                    request.purpose(),
                    amount
            );

            attempt.setRecipientCode(recipientCode);
            mongoTemplate.save(attempt);

            if (request.purpose() == TransactionPurpose.CAPITAL) {
                withdrawalItem(attempt, null, amount);

            } else {
                for (CommitmentReturn item : selected) {
                    item.setStatus(CommitmentReturnStatus.RESERVED);
                    item.setTransactionId(attempt.getId());

                    mongoTemplate.save(item);

                    withdrawalItem(
                            attempt,
                            item.getId(),
                            item.getAmount()
                    );
                }
            }

            commitment.setActiveTransactionId(attempt.getId());
            save(commitment);

            audit(
                    "WITHDRAWAL_INITIATED",
                    user.getId(),
                    commitment,
                    attempt,
                    ip,
                    Map.of(),
                    Map.of(
                            "amount", amount.toPlainString(),
                            "purpose", attempt.getPurpose().name(),
                            "status", "PENDING"
                    )
            );

            notifyUsers(
                    commitment,
                    "WITHDRAWAL_INITIATED",
                    "Withdrawal processing",
                    "Your "
                            + attempt.getPurpose().name().toLowerCase(Locale.ROOT)
                            + " withdrawal of "
                            + money(amount)
                            + " is processing. Reference: "
                            + attempt.getReference()
                            + ".",
                    true
            );

            return new PreparedWithdrawal(attempt, true);
        });

        if (prepared.created()) {
            try {
                data(
                        payStackService.initiateTransfer(
                                Map.of(
                                        "source", "balance",
                                        "amount",
                                        CommitmentRules.kobo(
                                                prepared.transaction().getAmount()
                                        ),
                                        "recipient", recipientCode,
                                        "reference",
                                        prepared.transaction().getReference(),
                                        "reason",
                                        "Echaris "
                                                + request.purpose().name()
                                                .toLowerCase(Locale.ROOT)
                                                + " withdrawal"
                                )
                        )
                );

            } catch (RuntimeException exception) {
                // Unknown provider acceptance keeps the funds reserved.
                log.warn(
                        "Payout submission unconfirmed: transactionId={}, errorType={}",
                        prepared.transaction().getId(),
                        exception.getClass().getSimpleName()
                );
            }
        }

        return transaction(prepared.transaction().getId());
    }

    public Transaction verify(
            String transactionId,
            boolean admin
    ) {
        User actor = admin ? requireAdmin() : customerUser();

        Transaction attempt = transaction(transactionId);

        if (!admin && !attempt.getUserId().equals(actor.getId())) {
            throw new ResourceNotFoundException("Transaction not found");
        }

        return verify(attempt, actor.getId(), currentIp());
    }

    private Transaction verify(
            Transaction attempt,
            String actorUserId,
            String ip
    ) {
        Map<?, ?> response = data(
                attempt.getDirection() == TransactionDirection.INCOMING
                        ? payStackService.verifyPayment(attempt.getReference())
                        : payStackService.verifyTransfer(attempt.getReference())
        );

        return applyProvider(attempt, response, actorUserId, ip);
    }

    public void webhook(
            String event,
            Map<?, ?> payload
    ) {
        String reference = Objects.toString(
                payload.get("reference"),
                ""
        );

        Transaction attempt = mongoTemplate.findOne(
                Query.query(Criteria.where("reference").is(reference)),
                Transaction.class
        );

        if (attempt == null) {
            return;
        }

        boolean incoming = event.equals("charge.success");

        if (incoming
                != (attempt.getDirection() == TransactionDirection.INCOMING)) {
            return;
        }

        String expected = incoming
                ? "success"
                : event.substring("transfer.".length());

        if (!expected.equals(
                Objects.toString(payload.get("status"), "")
        )) {
            throw new InvalidOperationException(
                    "Paystack event status does not match its payload"
            );
        }

        applyProvider(attempt, payload, null, null);
    }

    private Transaction applyProvider(
            Transaction attempt,
            Map<?, ?> payload,
            String actorUserId,
            String ip
    ) {
        if (!attempt.getReference().equals(payload.get("reference"))
                || !"NGN".equals(payload.get("currency"))
                || payload.get("amount") == null
                || new BigDecimal(payload.get("amount").toString())
                .compareTo(
                        BigDecimal.valueOf(
                                CommitmentRules.kobo(attempt.getAmount())
                        )
                ) != 0) {
            throw new InvalidOperationException(
                    "Paystack amount or reference does not match this transaction"
            );
        }

        if (attempt.getDirection() == TransactionDirection.OUTGOING) {
            Object recipient = payload.get("recipient");

            String code = recipient instanceof Map<?, ?> map
                    ? Objects.toString(map.get("recipient_code"), "")
                    : Objects.toString(recipient, "");

            if (!Objects.equals(attempt.getRecipientCode(), code)) {
                throw new InvalidOperationException(
                        "Paystack payout recipient does not match"
                );
            }
        }

        String providerStatus = Objects.toString(
                payload.get("status"),
                ""
        ).toLowerCase(Locale.ROOT);

        TransactionStatus result = switch (providerStatus) {
            case "success" -> TransactionStatus.SUCCESS;
            case "failed" -> TransactionStatus.FAILED;
            case "reversed", "refunded" -> TransactionStatus.REVERSED;
            default -> null;
        };

        if (result == null) {
            mongoTemplate.updateFirst(
                    Query.query(
                            Criteria.where("id")
                                    .is(attempt.getId())
                                    .and("status")
                                    .is(TransactionStatus.PENDING)
                    ),
                    new Update()
                            .set("providerStatus", providerStatus)
                            .set("updatedAt", LocalDateTime.now()),
                    Transaction.class
            );

            return transaction(attempt.getId());
        }

        Instant paidAt = result == TransactionStatus.SUCCESS
                && attempt.getDirection() == TransactionDirection.INCOMING
                ? Instant.parse(
                Objects.toString(payload.get("paid_at"), "")
        )
                : Instant.now();

        return inTransaction(() -> {
            Transaction current = transaction(attempt.getId());

            if (current.getStatus() == result
                    || current.getStatus() == TransactionStatus.REVERSED) {
                return current;
            }

            Commitment commitment = byId(current.getCommitmentId());

            if (current.getStatus() != TransactionStatus.PENDING
                    && !(current.getStatus() == TransactionStatus.SUCCESS
                    && result == TransactionStatus.REVERSED)) {

                if (!providerStatus.equals(current.getProviderStatus())) {
                    String previousStatus = current.getStatus().name();

                    commitment.setStatus(CommitmentStatus.REVIEW_REQUIRED);
                    save(commitment);

                    if (result == TransactionStatus.SUCCESS) {
                        // Record actual money, but freeze allocation
                        // when the provider contradicts a previous failure.
                        confirmedPayment(current, paidAt);

                        current.setStatus(TransactionStatus.SUCCESS);
                        current.setCompletedAt(Instant.now());
                    }

                    current.setProviderStatus(providerStatus);
                    mongoTemplate.save(current);

                    audit(
                            "PROVIDER_STATUS_CONFLICT",
                            actorUserId,
                            commitment,
                            current,
                            ip,
                            Map.of("status", previousStatus),
                            Map.of(
                                    "status", current.getStatus().name(),
                                    "providerStatus", providerStatus
                            )
                    );

                    notifyUsers(
                            commitment,
                            "PROVIDER_STATUS_CONFLICT",
                            "Payment review required",
                            "A provider status conflict requires review. Reference: "
                                    + current.getReference()
                                    + ".",
                            true
                    );
                }

                return current;
            }

            TransactionStatus previous = current.getStatus();

            current.setStatus(result);
            current.setProviderStatus(providerStatus);
            current.setCompletedAt(Instant.now());
            current.setUpdatedAt(LocalDateTime.now());

            mongoTemplate.save(current);

            if (result == TransactionStatus.SUCCESS) {
                confirmedPayment(current, paidAt);

                if (current.getDirection() == TransactionDirection.INCOMING) {
                    if (commitment.getFundedAt() != null
                            || commitment.getStatus()
                            == CommitmentStatus.REVIEW_REQUIRED) {

                        commitment.setStatus(CommitmentStatus.REVIEW_REQUIRED);

                        audit(
                                "ADDITIONAL_FUNDING_REQUIRES_REVIEW",
                                actorUserId,
                                commitment,
                                current,
                                ip,
                                Map.of(),
                                Map.of(
                                        "amount",
                                        current.getAmount().toPlainString()
                                )
                        );

                    } else {
                        commitment.setFundedAt(paidAt);

                        commitment.setMaturityAt(
                                CommitmentRules.maturity(
                                        paidAt,
                                        commitment.getDurationMonths()
                                )
                        );

                        commitment.setStatus(CommitmentStatus.LOCKED);

                        for (int month = 1;
                             month <= commitment.getDurationMonths();
                             month++) {

                            CommitmentReturn item = new CommitmentReturn();

                            item.setId(UUID.randomUUID().toString());
                            item.setCommitmentId(commitment.getId());
                            item.setUserId(commitment.getUserId());
                            item.setMonthNumber(month);

                            item.setAmount(
                                    CommitmentRules.monthlyReturn(
                                            commitment.getCapital(),
                                            commitment.getMonthlyRate()
                                    )
                            );

                            mongoTemplate.insert(item);
                        }
                    }

                } else {
                    settleItems(current, true);

                    if (current.getPurpose() == TransactionPurpose.CAPITAL) {
                        if (commitment.getStatus()
                                != CommitmentStatus.REVIEW_REQUIRED) {
                            commitment.setStatus(CommitmentStatus.WITHDRAWN);
                        }

                        commitment.setCapitalWithdrawnAt(Instant.now());
                    }
                }

            } else if (current.getDirection() == TransactionDirection.INCOMING
                    && result == TransactionStatus.REVERSED) {

                commitment.setStatus(CommitmentStatus.REVIEW_REQUIRED);

                mongoTemplate.updateFirst(
                        Query.query(
                                Criteria.where("transactionId")
                                        .is(current.getId())
                        ),
                        new Update()
                                .set("status", PaymentStatus.REVERSED)
                                .set("reversedAt", Instant.now()),
                        Payment.class
                );

            } else if (current.getDirection() == TransactionDirection.OUTGOING) {
                settleItems(current, false);

                if (result == TransactionStatus.REVERSED) {
                    mongoTemplate.updateFirst(
                            Query.query(
                                    Criteria.where("transactionId")
                                            .is(current.getId())
                            ),
                            new Update()
                                    .set("status", PaymentStatus.REVERSED)
                                    .set("reversedAt", Instant.now()),
                            Payment.class
                    );

                    if (current.getPurpose() == TransactionPurpose.CAPITAL) {
                        commitment.setCapitalWithdrawnAt(null);

                        if (commitment.getStatus()
                                != CommitmentStatus.REVIEW_REQUIRED) {
                            commitment.setStatus(CommitmentStatus.MATURED);
                        }
                    }
                }
            }

            if (Objects.equals(
                    commitment.getActiveTransactionId(),
                    current.getId()
            )) {
                commitment.setActiveTransactionId(null);
            }

            save(commitment);

            String event = (
                    current.getDirection() == TransactionDirection.INCOMING
                            ? "FUNDING_"
                            : "WITHDRAWAL_"
            ) + result.name();

            audit(
                    event,
                    actorUserId,
                    commitment,
                    current,
                    ip,
                    Map.of("status", previous.name()),
                    Map.of(
                            "status", result.name(),
                            "amount", current.getAmount().toPlainString(),
                            "purpose", current.getPurpose().name()
                    )
            );

            String message;

            if (commitment.getStatus() == CommitmentStatus.REVIEW_REQUIRED) {
                message = "Payment status requires review. Reference: "
                        + current.getReference()
                        + ".";

            } else if (current.getDirection() == TransactionDirection.INCOMING
                    && result == TransactionStatus.SUCCESS) {

                message = "Funding of "
                        + money(current.getAmount())
                        + " confirmed. Capital is locked until "
                        + commitment.getMaturityAt()
                        + ". Total interest available: "
                        + money(
                        CommitmentRules.monthlyReturn(
                                commitment.getCapital(),
                                commitment.getMonthlyRate()
                        ).multiply(
                                BigDecimal.valueOf(
                                        commitment.getDurationMonths()
                                )
                        )
                )
                        + ".";

            } else {
                message = current.getPurpose().name()
                        + " "
                        + current.getDirection().name().toLowerCase(Locale.ROOT)
                        + " payment of "
                        + money(current.getAmount())
                        + ": "
                        + result.name().toLowerCase(Locale.ROOT)
                        + ".";
            }

            notifyUsers(
                    commitment,
                    event,
                    event.replace('_', ' '),
                    message,
                    current.getDirection() == TransactionDirection.OUTGOING
                            || result == TransactionStatus.SUCCESS
            );

            return current;
        });
    }

    private void settleItems(
            Transaction attempt,
            boolean success
    ) {
        List<WithdrawalItem> items = mongoTemplate.find(
                Query.query(
                        Criteria.where("transactionId").is(attempt.getId())
                ),
                WithdrawalItem.class
        );

        if (items.isEmpty()) {
            throw new InvalidOperationException(
                    "Withdrawal allocation is missing"
            );
        }

        for (WithdrawalItem item : items) {
            if (item.getReturnId() != null) {
                CommitmentReturn interest = mongoTemplate.findById(
                        item.getReturnId(),
                        CommitmentReturn.class
                );

                if (interest == null
                        || !attempt.getId().equals(
                        interest.getTransactionId()
                )) {
                    throw new InvalidOperationException(
                            "Withdrawal allocation changed; review this transaction"
                    );
                }

                interest.setStatus(
                        success
                                ? CommitmentReturnStatus.PAID
                                : CommitmentReturnStatus.AVAILABLE
                );

                if (!success) {
                    interest.setTransactionId(null);
                }

                mongoTemplate.save(interest);
            }

            item.setStatus(
                    success
                            ? WithdrawalItemStatus.PAID
                            : WithdrawalItemStatus.RELEASED
            );

            mongoTemplate.save(item);
        }
    }

    private void confirmedPayment(
            Transaction attempt,
            Instant paidAt
    ) {
        Payment payment = new Payment();

        payment.setId(UUID.randomUUID().toString());
        payment.setTransactionId(attempt.getId());
        payment.setCommitmentId(attempt.getCommitmentId());
        payment.setUserId(attempt.getUserId());
        payment.setDirection(attempt.getDirection());
        payment.setPurpose(attempt.getPurpose());
        payment.setAmount(attempt.getAmount());
        payment.setReference(attempt.getReference());
        payment.setPaidAt(paidAt);

        mongoTemplate.insert(payment);
    }

    private Transaction newTransaction(
            Commitment commitment,
            String key,
            String fingerprint,
            TransactionDirection direction,
            TransactionPurpose purpose,
            BigDecimal amount
    ) {
        Transaction attempt = new Transaction();

        attempt.setId(UUID.randomUUID().toString());
        attempt.setCommitmentId(commitment.getId());
        attempt.setUserId(commitment.getUserId());
        attempt.setRequestKey(key);
        attempt.setRequestFingerprint(fingerprint);
        attempt.setDirection(direction);
        attempt.setPurpose(purpose);
        attempt.setAmount(amount);
        attempt.setReference("EC-" + UUID.randomUUID());

        mongoTemplate.insert(attempt);

        return attempt;
    }

    private void withdrawalItem(
            Transaction attempt,
            String returnId,
            BigDecimal amount
    ) {
        WithdrawalItem item = new WithdrawalItem();

        item.setId(UUID.randomUUID().toString());
        item.setCommitmentId(attempt.getCommitmentId());
        item.setTransactionId(attempt.getId());
        item.setReturnId(returnId);
        item.setPurpose(attempt.getPurpose());
        item.setAmount(amount);

        mongoTemplate.insert(item);
    }

    private void notifyUsers(
            Commitment commitment,
            String event,
            String title,
            String message,
            boolean admins
    ) {
        Set<String> recipients = new LinkedHashSet<>();
        recipients.add(commitment.getUserId());

        if (admins) {
            adminRepository.findAllByDeleted(false)
                    .stream()
                    .filter(admin -> admin.isActive())
                    .forEach(admin ->
                            recipients.add(admin.getUserId())
                    );
        }

        for (User recipient : userRepository.findAllById(recipients)) {
            if (recipient.isDeleted()
                    || !recipient.isActive()
                    || !recipient.isVerified()) {
                continue;
            }

            if (!recipient.getId().equals(commitment.getUserId())
                    && recipient.getUserType() != UserType.ADMIN) {
                continue;
            }

            String body = recipient.getId().equals(commitment.getUserId())
                    ? message
                    : "Customer user ID: "
                    + commitment.getUserId()
                    + ". "
                    + message;

            notificationService.create(
                    recipient.getId(),
                    event,
                    title,
                    body,
                    "COMMITMENT",
                    commitment.getId(),
                    Map.of(
                            "commitmentId", commitment.getId(),
                            "customerUserId", commitment.getUserId()
                    )
            );

            emailSenderService.sendTemplateEmailAfterCommit(
                    recipient.getEmailAddress(),
                    title,
                    "commitment-event",
                    Map.of(
                            "name",
                            recipient.getFirstName() == null
                                    ? "there"
                                    : recipient.getFirstName(),
                            "title", title,
                            "message", body
                    )
            );
        }
    }

    private void audit(
            String event,
            String actor,
            Commitment commitment,
            Transaction attempt,
            String ip,
            Map<String, String> before,
            Map<String, String> after
    ) {
        auditService.record(
                event,
                actor,
                commitment.getUserId(),
                "COMMITMENT",
                commitment.getId(),
                attempt == null ? null : attempt.getId(),
                ip,
                before,
                after
        );
    }

    @Scheduled(fixedDelay = 60000)
    public void matureCommitments() {
        List<Commitment> due = mongoTemplate.find(
                Query.query(
                        Criteria.where("status")
                                .is(CommitmentStatus.LOCKED)
                                .and("maturityAt")
                                .lte(Instant.now())
                ).limit(100),
                Commitment.class
        );

        for (Commitment item : due) {
            try {
                inTransaction(() -> {
                    Commitment current = byId(item.getId());

                    if (current.getStatus() == CommitmentStatus.LOCKED
                            && CommitmentRules.matured(
                            current.getMaturityAt(),
                            Instant.now()
                    )) {
                        mature(current);
                        save(current);
                    }

                    return null;
                });

            } catch (RuntimeException exception) {
                log.warn(
                        "Maturity update failed: commitmentId={}, errorType={}",
                        item.getId(),
                        exception.getClass().getSimpleName()
                );
            }
        }
    }

    private void mature(Commitment commitment) {
        if (commitment.getStatus() != CommitmentStatus.LOCKED) {
            return;
        }

        commitment.setStatus(CommitmentStatus.MATURED);

        audit(
                "COMMITMENT_MATURED",
                null,
                commitment,
                null,
                null,
                Map.of("status", "LOCKED"),
                Map.of("status", "MATURED")
        );

        notifyUsers(
                commitment,
                "COMMITMENT_MATURED",
                "Capital available for withdrawal",
                "Your commitment "
                        + commitment.getName()
                        + " has matured. The full capital of "
                        + money(commitment.getCapital())
                        + " is available for withdrawal.",
                true
        );
    }

    @Scheduled(fixedDelay = 60000)
    public void reconcilePending() {
        List<Transaction> pending = mongoTemplate.find(
                Query.query(
                                Criteria.where("status").is(TransactionStatus.PENDING)
                        )
                        .with(Sort.by("updatedAt"))
                        .limit(5),
                Transaction.class
        );

        for (Transaction attempt : pending) {
            try {
                verify(attempt, null, null);

            } catch (RuntimeException exception) {
                log.warn(
                        "Payment status unconfirmed: transactionId={}, errorType={}",
                        attempt.getId(),
                        exception.getClass().getSimpleName()
                );

                mongoTemplate.updateFirst(
                        Query.query(
                                Criteria.where("id")
                                        .is(attempt.getId())
                                        .and("status")
                                        .is(TransactionStatus.PENDING)
                        ),
                        new Update().set(
                                "updatedAt",
                                LocalDateTime.now()
                        ),
                        Transaction.class
                );
            }
        }
    }

    private Commitment byId(String id) {
        Commitment commitment = mongoTemplate.findById(
                id,
                Commitment.class
        );

        if (commitment == null) {
            throw new ResourceNotFoundException("Commitment not found");
        }

        return commitment;
    }

    private Commitment owned(
            String id,
            String userId
    ) {
        Commitment commitment = byId(id);

        if (!commitment.getUserId().equals(userId)) {
            throw new ResourceNotFoundException("Commitment not found");
        }

        return commitment;
    }

    private Transaction transaction(String id) {
        Transaction attempt = mongoTemplate.findById(
                id,
                Transaction.class
        );

        if (attempt == null) {
            throw new ResourceNotFoundException("Transaction not found");
        }

        return attempt;
    }

    private Transaction byKey(
            String userId,
            String key
    ) {
        return mongoTemplate.findOne(
                Query.query(
                        Criteria.where("userId")
                                .is(userId)
                                .and("requestKey")
                                .is(key)
                ),
                Transaction.class
        );
    }

    private Transaction sameRequest(
            Transaction attempt,
            String fingerprint
    ) {
        if (!Objects.equals(
                attempt.getRequestFingerprint(),
                fingerprint
        )) {
            throw new InvalidInputException(
                    "This Idempotency-Key was already used for another request"
            );
        }

        return attempt;
    }

    private void save(Commitment commitment) {
        commitment.setUpdatedAt(LocalDateTime.now());
        mongoTemplate.save(commitment);
    }

    private <T> PaginatedData page(
            Class<T> type,
            Criteria criteria,
            int page,
            int size
    ) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) {
            throw new InvalidInputException("Invalid page or size");
        }

        long total = mongoTemplate.count(
                new Query(criteria),
                type
        );

        String timeField = type == AuditLog.class
                ? "occurredAt"
                : type == CommitmentRateHistory.class
                ? "changedAt"
                : type == Payment.class
                ? "paidAt"
                : "createdAt";

        List<T> rows = mongoTemplate.find(
                new Query(criteria)
                        .with(
                                Sort.by(
                                        Sort.Order.desc(timeField),
                                        Sort.Order.asc("id")
                                )
                        )
                        .skip((long) (page - 1) * size)
                        .limit(size),
                type
        );

        return utilsService.createPaginatedData(
                rows,
                page,
                size,
                total
        );
    }

    private Map<?, ?> data(
            Map<String, Object> response
    ) {
        if (!Boolean.TRUE.equals(response.get("status"))
                || !(response.get("data") instanceof Map<?, ?> data)) {
            throw new InvalidOperationException(
                    "Paystack did not confirm this request"
            );
        }

        return data;
    }

    private void key(String value) {
        if (value == null
                || !value.matches("[A-Za-z0-9_-]{8,100}")) {
            throw new InvalidInputException(
                    "Provide a unique Idempotency-Key of 8-100 letters, digits, hyphens or underscores"
            );
        }
    }

    private String fingerprint(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(
                                    value.getBytes(StandardCharsets.UTF_8)
                            )
            );

        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String money(BigDecimal value) {
        return "₦" + value.setScale(2).toPlainString();
    }

    private <T> T inTransaction(Supplier<T> action) {
        for (int attempt = 0; ; attempt++) {
            try {
                return new TransactionTemplate(transactionManager)
                        .execute(status -> action.get());

            } catch (DataAccessException exception) {
                boolean retry =
                        exception instanceof OptimisticLockingFailureException
                                || exception instanceof DuplicateKeyException;

                for (Throwable cause = exception;
                     cause != null;
                     cause = cause.getCause()) {

                    if (cause instanceof com.mongodb.MongoException mongo
                            && mongo.hasErrorLabel(
                            "TransientTransactionError"
                    )) {
                        retry = true;
                    }
                }

                if (!retry || attempt >= 4) {
                    throw exception;
                }

                try {
                    Thread.sleep(25L << attempt);

                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw exception;
                }
            }
        }
    }
}