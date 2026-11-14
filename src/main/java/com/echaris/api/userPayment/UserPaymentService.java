package com.echaris.api.userPayment;

import com.echaris.api.admin.Admin;
import com.echaris.api.admin.AdminRepository;
import com.echaris.api.commitment.Commitment;
import com.echaris.api.commitment.CommitmentReturn;
import com.echaris.api.commitment.CommitmentReturnStatus;
import com.echaris.api.commitment.CommitmentRules;
import com.echaris.api.commitment.CommitmentService;
import com.echaris.api.commitment.CommitmentStatus;
import com.echaris.api.marketplace.MarketplaceFinancingService;
import com.echaris.api.notification.NotificationService;
import com.echaris.api.payoutaccount.CustomerPayoutAccount;
import com.echaris.api.payoutaccount.response.CustomerPayoutAccountResponse;
import com.echaris.api.userPayment.request.UserPaymentPayoutRequest;
import com.echaris.api.userPayment.request.UserPaymentRequest;
import com.echaris.api.userauthmgt.user.User;
import com.echaris.api.userauthmgt.user.UserRepository;
import com.echaris.api.userauthmgt.user.UserType;
import com.echaris.api.utils.PaginatedData;
import com.echaris.api.utils.UtilsService;
import com.echaris.api.utils.emailsenderservice.EmailSenderService;
import com.echaris.api.utils.exception.InvalidInputException;
import com.echaris.api.utils.exception.InvalidOperationException;
import com.echaris.api.utils.exception.ResourceExistsException;
import com.echaris.api.utils.exception.ResourceNotFoundException;
import com.echaris.api.utils.exception.UnauthorizedException;
import com.echaris.api.utils.fileupload.ManagedFileRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserPaymentService {

    private static final String PAYMENT_REFERENCE = "USER_PAYMENT";

    private final MongoTemplate mongoTemplate;
    private final CommitmentService commitmentService;
    private final MarketplaceFinancingService marketplaceFinancingService;
    private final ManagedFileRepository managedFileRepository;
    private final UtilsService utilsService;
    private final AdminRepository adminRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailSenderService emailSenderService;

    @PostConstruct
    public void createIndexes() {
        var indexes = mongoTemplate.indexOps(UserPayment.class);
        indexes.createIndex(new Index().on("userId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        indexes.createIndex(new Index().on("status", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));

        backfillDirection(UserPaymentDirection.INCOMING, UserPaymentPurpose.COMMITMENT,
                UserPaymentPurpose.ORDER, UserPaymentPurpose.MARKETPLACE_FINANCING);
        backfillDirection(UserPaymentDirection.OUTGOING, UserPaymentPurpose.CUSTOMER_PAYOUT);
        indexes.createIndex(new Index().on("direction", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        indexes.createIndex(new Index()
                .on("transferReference", Sort.Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(new Document("direction", UserPaymentDirection.OUTGOING.name())
                        .append("transferReference", new Document("$type", "string"))))
                .named("uq_user_payment_payout_reference"));
    }

    private void backfillDirection(UserPaymentDirection direction, UserPaymentPurpose... purposes) {
        Query query = Query.query(Criteria.where("direction").is(null).and("purpose").in(purposes));
        mongoTemplate.updateMulti(query, new Update().set("direction", direction), UserPayment.class);
    }

    @Transactional
    public UserPayment createUserManualPayment(UserPaymentRequest request) {
        User customer = requireCustomer();
        requireSupportedFundingPurpose(request.purpose());
        String targetId = request.targetId().strip();
        BigDecimal amount = BigDecimal.valueOf(CommitmentRules.kobo(request.amount()), 2);
        String targetName = null;

        if (request.purpose() == UserPaymentPurpose.MARKETPLACE_FINANCING) {
            var financing = marketplaceFinancingService.getUnpaidFinancing(targetId, customer.getId());
            if (amount.compareTo(financing.getCapital()) != 0) {
                throw new InvalidInputException("The submitted amount must match the marketplace capital amount");
            }
        } else {
            Commitment commitment = commitmentService.get(targetId);
            if (commitment.getStatus() != CommitmentStatus.AWAITING_PAYMENT || commitment.getFundedAt() != null) {
                throw new InvalidOperationException("This commitment is not awaiting payment");
            }
            if (amount.compareTo(commitment.getCapital()) != 0) {
                throw new InvalidInputException("The submitted amount must match the commitment amount");
            }
            targetId = commitment.getId();
            targetName = commitment.getName();
        }

        UserPayment payment = new UserPayment();
        payment.setId(UUID.randomUUID().toString());
        payment.setUserId(customer.getId());
        payment.setPurpose(request.purpose());
        payment.setDirection(UserPaymentDirection.INCOMING);
        payment.setTargetId(targetId);
        payment.setAmount(amount);
        payment.setBankName(request.bankName().strip());
        payment.setAccountName(request.accountName().strip());
        payment.setAccountNumber(request.accountNumber());
        payment.setReceiptUrls(request.receiptUrls().stream().map(this::requireReceiptUrl).distinct().toList());

        String reference = request.transferReference();
        payment.setTransferReference(reference == null || reference.isBlank() ? null : reference.strip());

        UserPayment saved = mongoTemplate.insert(payment);
        if (saved.getPurpose() == UserPaymentPurpose.MARKETPLACE_FINANCING) {
            marketplaceFinancingService.makePaymentForMarketFinancing(saved.getTargetId(), saved.getId());
            return saved;
        }

        notifyAdminsOfSubmission(saved, customer, targetName);
        notifyCustomer(saved, targetName, "USER_PAYMENT_UNDER_REVIEW", "Payment under review",
                "We received your %s. An admin will review your receipt."
                        .formatted(paymentDescription(saved, targetName)));
        return saved;
    }

    public CustomerPayoutAccountResponse getPayoutAccount(String customerUserId) {
        commitmentService.requireAdmin();
        User customer = requirePayoutCustomer(customerUserId);
        return CustomerPayoutAccountResponse.from(requirePayoutAccount(customer.getId()));
    }

    @Transactional
    public UserPayment recordPayout(UserPaymentPayoutRequest request) {
        User admin = commitmentService.requireAdmin();
        User customer = requirePayoutCustomer(request.customerUserId());
        CustomerPayoutAccount account = requirePayoutAccount(customer.getId());
        if (!Objects.equals(account.getVersion(), request.payoutAccountVersion())) {
            throw new OptimisticLockingFailureException("The payout account changed");
        }
        var details = account.getDetails();
        Instant now = Instant.now();
        if (request.paidAt().isAfter(now)) {
            throw new InvalidInputException("Payout time cannot be in the future");
        }

        UserPayment payment = new UserPayment();
        payment.setId(UUID.randomUUID().toString());
        payment.setUserId(customer.getId());
        payment.setPurpose(UserPaymentPurpose.CUSTOMER_PAYOUT);
        payment.setPayoutType(request.payoutType());
        payment.setTargetId(request.targetId().strip());
        payment.setDirection(UserPaymentDirection.OUTGOING);
        payment.setStatus(UserPaymentStatus.PAID);
        payment.setAmount(BigDecimal.valueOf(CommitmentRules.kobo(request.amount()), 2));
        payment.setBankName(details.bankName());
        payment.setAccountName(details.accountName());
        payment.setAccountNumber(details.accountNumber());
        payment.setReceiptUrls(request.receiptUrls().stream().map(this::requireReceiptUrl).distinct().toList());
        payment.setTransferReference(request.transferReference().strip());
        payment.setPaidAt(request.paidAt().truncatedTo(ChronoUnit.MILLIS));
        payment.setRecordedByUserId(admin.getId());

        UserPayment saved;
        try {
            saved = mongoTemplate.insert(payment);
        } catch (DuplicateKeyException exception) {
            throw new ResourceExistsException("This payout transfer reference has already been recorded");
        }

        switch (saved.getPayoutType()) {
            case COMMITMENT_CAPITAL -> settleCapitalPayout(saved);
            case COMMITMENT_RETURN -> settleReturnPayout(saved);
            case MARKETPLACE_CAPITAL -> marketplaceFinancingService.recordCapitalPayout(saved);
        }

        notifyPayoutCustomer(saved, customer);
        return saved;
    }

    private void settleCapitalPayout(UserPayment payment) {
        Commitment commitment = requirePayoutCommitment(payment.getTargetId(), payment);
        if (commitment.getStatus() == CommitmentStatus.WITHDRAWN
                || commitment.getCapitalWithdrawnAt() != null
                || !CommitmentRules.matured(commitment.getMaturityAt(), payment.getPaidAt())) {
            throw new InvalidOperationException("Commitment capital has not matured or was already paid out");
        }
        requirePayoutAmount(payment, commitment.getCapital());

        commitment.setStatus(CommitmentStatus.WITHDRAWN);
        commitment.setCapitalWithdrawnAt(payment.getPaidAt());
        commitment.setUpdatedAt(LocalDateTime.now());
        mongoTemplate.save(commitment);
    }

    private void settleReturnPayout(UserPayment payment) {
        Query query = Query.query(Criteria.where("id").is(payment.getTargetId())
                .and("userId").is(payment.getUserId()));
        CommitmentReturn installment = mongoTemplate.findOne(query, CommitmentReturn.class);
        if (installment == null) {
            throw new ResourceNotFoundException("Commitment return not found for this customer");
        }
        Commitment commitment = requirePayoutCommitment(installment.getCommitmentId(), payment);
        if (installment.getStatus() != CommitmentReturnStatus.AVAILABLE
                || installment.getTransactionId() != null) {
            throw new InvalidOperationException("Commitment return is already paid or reserved");
        }
        requirePayoutAmount(payment, installment.getAmount());

        // Update the versioned parent, which the automated withdrawal flow also writes.
        commitment.setUpdatedAt(LocalDateTime.now());
        mongoTemplate.save(commitment);
        installment.setStatus(CommitmentReturnStatus.PAID);
        installment.setUpdatedAt(LocalDateTime.now());
        mongoTemplate.save(installment);
    }

    private Commitment requirePayoutCommitment(String commitmentId, UserPayment payment) {
        Query query = Query.query(Criteria.where("id").is(commitmentId)
                .and("userId").is(payment.getUserId()));
        Commitment commitment = mongoTemplate.findOne(query, Commitment.class);
        if (commitment == null) {
            throw new ResourceNotFoundException("Commitment not found for this customer");
        }
        if (commitment.getFundedAt() == null
                || commitment.getStatus() == CommitmentStatus.AWAITING_PAYMENT
                || commitment.getStatus() == CommitmentStatus.REVIEW_REQUIRED
                || commitment.getActiveTransactionId() != null) {
            throw new InvalidOperationException("Commitment is unavailable or has a transaction in progress");
        }
        if (payment.getPaidAt().isBefore(commitment.getFundedAt())) {
            throw new InvalidInputException("Payout time cannot precede commitment funding");
        }
        return commitment;
    }

    private void requirePayoutAmount(UserPayment payment, BigDecimal expectedAmount) {
        if (expectedAmount == null || payment.getAmount().compareTo(expectedAmount) != 0) {
            throw new InvalidInputException("Payout amount must match the amount due");
        }
    }

    public PaginatedData getMyPayments(
            String search, UserPaymentStatus status, UserPaymentDirection direction,
            UserPaymentPurpose purpose, int page, int size
    ) {
        String userId = requireCustomer().getId();
        return page(paymentQuery(search, status, direction, purpose, userId), page, size);
    }

    public PaginatedData getAdminPayments(
            String search, UserPaymentStatus status, UserPaymentDirection direction,
            UserPaymentPurpose purpose, String customerUserId, int page, int size
    ) {
        commitmentService.requireAdmin();
        return page(paymentQuery(search, status, direction, purpose, customerUserId), page, size);
    }

    public UserPayment getAdminPayment(String paymentId) {

        return findById(paymentId);
    }

    @Transactional
    public UserPayment approve(String paymentId) {
        User admin = commitmentService.requireAdmin();
        UserPayment payment = findById(paymentId);
        requireSupportedFundingPurpose(payment.getPurpose());

        if (payment.getDirection() != UserPaymentDirection.INCOMING) {
            throw new InvalidOperationException("Only incoming payments can be approved");
        }
        if (payment.getPurpose() == UserPaymentPurpose.MARKETPLACE_FINANCING) {
            return marketplaceFinancingService.adminUpdatesMarketFinancingPaymentStatus(
                    paymentId, UserPaymentStatus.APPROVED, null);
        }
        if (payment.getStatus() == UserPaymentStatus.APPROVED) {
            return payment;
        }

        requirePending(payment);
        if (payment.getAmount() == null || payment.getAmount().signum() <= 0) {
            throw new InvalidInputException("A positive payment amount is required");
        }

        Instant reviewedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        recordReview(payment, UserPaymentStatus.APPROVED, admin.getId(), reviewedAt);
        UserPayment saved = mongoTemplate.save(payment);
        commitmentService.confirmManualFunding(
                saved.getTargetId(), saved.getUserId(), saved.getAmount(),
                saved.getId(), reviewedAt, admin.getId());

        String targetName = findTargetName(saved);
        notifyCustomer(saved, targetName, "USER_PAYMENT_APPROVED", "Payment approved",
                "Your %s was approved. Your commitment is now funded."
                        .formatted(paymentDescription(saved, targetName)));
        return saved;
    }

    @Transactional
    public UserPayment reject(String paymentId, String reasonForRejection) {
        User admin = commitmentService.requireAdmin();
        UserPayment payment = findById(paymentId);
        requireIncomingPayment(payment.getPurpose());

        if (payment.getPurpose() == UserPaymentPurpose.MARKETPLACE_FINANCING) {
            return marketplaceFinancingService.adminUpdatesMarketFinancingPaymentStatus(
                    paymentId, UserPaymentStatus.REJECTED, reasonForRejection);
        }
        if (payment.getStatus() == UserPaymentStatus.REJECTED) {
            return payment;
        }

        requirePending(payment);
        recordReview(payment, UserPaymentStatus.REJECTED, admin.getId(), Instant.now());

        UserPayment saved = mongoTemplate.save(payment);
        String commitmentName = findTargetName(saved);
        notifyCustomer(saved, commitmentName, "USER_PAYMENT_REJECTED", "Payment rejected",
                "Your %s was rejected. Check your payment details before submitting another receipt."
                        .formatted(paymentDescription(saved, commitmentName)));
        return saved;
    }

    private void recordReview(UserPayment payment, UserPaymentStatus status, String adminId, Instant reviewedAt) {
        payment.setStatus(status);
        payment.setReviewedByUserId(adminId);
        payment.setReviewedAt(reviewedAt.truncatedTo(ChronoUnit.MILLIS));
        payment.setUpdatedAt(LocalDateTime.now());
    }

    private void notifyPayoutCustomer(UserPayment payment, User customer) {
        String title = "Payout sent";
        String message = "Echaris sent your NGN %s payout on %s. Transfer reference %s."
                .formatted(payment.getAmount().toPlainString(), payment.getPaidAt(), payment.getTransferReference());
        Map<String, String> data = notificationData(payment, null);
        notificationService.create(customer.getId(), "CUSTOMER_PAYOUT_PAID", title, message,
                PAYMENT_REFERENCE, payment.getId(), data);

        if (customer.getEmailAddress() != null && !customer.getEmailAddress().isBlank()) {
            emailSenderService.sendTemplateEmailAfterCommit(customer.getEmailAddress(), title,
                    "customer-payout-paid", data);
        }
    }

    private void notifyAdminsOfSubmission(UserPayment payment, User customer, String commitmentName) {
        List<String> adminUserIds = adminRepository.findAllByDeleted(false).stream()
                .filter(Admin::isActive)
                .map(Admin::getUserId)
                .distinct()
                .toList();

        String title = "New payment submitted";
        Map<String, String> data = notificationData(payment, commitmentName);
        putIfPresent(data, "customerEmail", customer.getEmailAddress());
        data.put("customerUserId", customer.getId());
        data.put("receiptCount", Integer.toString(payment.getReceiptUrls().size()));
        data.put("detailsPath", "/api/v1/user-payments/private/admin/" + payment.getId());

        String customerLabel = data.getOrDefault("customerEmail", customer.getId());
        String message = "%s submitted a %s. Open the payment details to review the receipt."
                .formatted(customerLabel, paymentDescription(payment, commitmentName));

        for (User admin : userRepository.findAllById(adminUserIds)) {
            if (admin.getUserType() != UserType.ADMIN
                    || !admin.isActive() || !admin.isVerified() || admin.isDeleted()) {
                continue;
            }

            notificationService.create(admin.getId(), "USER_PAYMENT_SUBMITTED", title, message,
                    PAYMENT_REFERENCE, payment.getId(), data);

            if (admin.getEmailAddress() != null && !admin.getEmailAddress().isBlank()) {
                emailSenderService.sendTemplateEmailAfterCommit(admin.getEmailAddress(), title,
                        "user-payment-submitted", Map.ofEntries(
                                Map.entry("paymentId", payment.getId()),
                                Map.entry("customerUserId", customer.getId()),
                                Map.entry("customerEmail", Objects.toString(customer.getEmailAddress(), "")),
                                Map.entry("purpose", payment.getPurpose().name()),
                                Map.entry("targetId", payment.getTargetId()),
                                Map.entry("amount", payment.getAmount().toPlainString()),
                                Map.entry("bankName", payment.getBankName()),
                                Map.entry("accountName", payment.getAccountName()),
                                Map.entry("accountNumber", payment.getAccountNumber()),
                                Map.entry("transferReference", Objects.toString(payment.getTransferReference(), "Not supplied")),
                                Map.entry("receiptUrls", String.join("\n", payment.getReceiptUrls())),
                                Map.entry("status", payment.getStatus().name()),
                                Map.entry("submittedAt", payment.getCreatedAt().toString())
                        ));
            }
        }
    }

    private void notifyCustomer(
            UserPayment payment, String commitmentName, String type, String title, String message
    ) {
        userRepository.findById(payment.getUserId())
                .filter(user -> !user.isDeleted())
                .ifPresent(user -> notificationService.create(user.getId(), type, title, message,
                        PAYMENT_REFERENCE, payment.getId(), notificationData(payment, commitmentName)));
    }

    private Map<String, String> notificationData(UserPayment payment, String commitmentName) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("paymentId", payment.getId());
        putIfPresent(data, "targetId", payment.getTargetId());
        putIfPresent(data, "direction", Objects.toString(payment.getDirection(), null));
        putIfPresent(data, "payoutType", Objects.toString(payment.getPayoutType(), null));
        data.put("purpose", payment.getPurpose().name());
        data.put("amount", payment.getAmount().toPlainString());
        data.put("currency", "NGN");
        data.put("status", payment.getStatus().name());
        String targetNameKey = payment.getPurpose() == UserPaymentPurpose.MARKETPLACE_FINANCING
                ? "financingName" : "commitmentName";
        putIfPresent(data, targetNameKey, commitmentName);
        putIfPresent(data, "bankName", payment.getBankName());
        putIfPresent(data, "accountName", payment.getAccountName());
        putIfPresent(data, "transferReference", payment.getTransferReference());
        putIfPresent(data, "submittedAt", Objects.toString(payment.getCreatedAt(), null));
        putIfPresent(data, "reviewedAt", Objects.toString(payment.getReviewedAt(), null));
        putIfPresent(data, "paidAt", Objects.toString(payment.getPaidAt(), null));

        String accountNumber = payment.getAccountNumber();
        if (accountNumber != null && accountNumber.length() >= 4) {
            data.put("accountNumberLast4", accountNumber.substring(accountNumber.length() - 4));
        }

        return data;
    }

    private String findTargetName(UserPayment payment) {
        if (payment.getPurpose() == UserPaymentPurpose.MARKETPLACE_FINANCING) {
            return marketplaceFinancingService.getCustomerFinancing(payment.getTargetId(), payment.getUserId()).getName();
        }
        if (payment.getPurpose() != UserPaymentPurpose.COMMITMENT) {
            return null;
        }

        Query query = Query.query(Criteria.where("id").is(payment.getTargetId())
                .and("userId").is(payment.getUserId()));
        Commitment commitment = mongoTemplate.findOne(query, Commitment.class);
        return commitment == null ? null : commitment.getName();
    }

    private String paymentDescription(UserPayment payment, String commitmentName) {
        String targetType = payment.getPurpose() == UserPaymentPurpose.MARKETPLACE_FINANCING
                ? "marketplace financing" : "commitment";
        String target = commitmentName == null || commitmentName.isBlank()
                ? targetType + " " + payment.getTargetId()
                : targetType + " \"" + commitmentName + "\"";
        return "NGN " + payment.getAmount().toPlainString() + " payment for " + target;
    }

    private void putIfPresent(Map<String, String> data, String key, String value) {
        if (value != null && !value.isBlank()) {
            data.put(key, value);
        }
    }

    private User requirePayoutCustomer(String customerUserId) {
        return userRepository.findByIdAndUserType(customerUserId.strip(), UserType.CUSTOMER)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Customer not found"));
    }

    private CustomerPayoutAccount requirePayoutAccount(String customerUserId) {
        CustomerPayoutAccount account = mongoTemplate.findById(customerUserId, CustomerPayoutAccount.class);
        if (account == null || account.getDetails() == null) {
            throw new ResourceNotFoundException("Customer payout account not found");
        }
        return account;
    }

    private User requireCustomer() {
        User user = commitmentService.currentUser();
        if (user.getUserType() != UserType.CUSTOMER) {
            throw new UnauthorizedException("Customer access is required");
        }
        return user;
    }

    public UserPayment findById(String paymentId) {
        UserPayment payment = mongoTemplate.findById(paymentId, UserPayment.class);
        if (payment == null) {
            throw new ResourceNotFoundException("Payment submission not found");
        }
        return payment;
    }

    private void requirePending(UserPayment payment) {
        if (payment.getStatus() != UserPaymentStatus.PENDING) {
            throw new InvalidOperationException("Only pending payments can be reviewed");
        }
    }

    private void requireSupportedFundingPurpose(UserPaymentPurpose purpose) {
        requireIncomingPayment(purpose);
        if (purpose != UserPaymentPurpose.COMMITMENT && purpose != UserPaymentPurpose.MARKETPLACE_FINANCING) {
            throw new InvalidOperationException("Manual order payments are not available yet");
        }
    }

    private void requireIncomingPayment(UserPaymentPurpose purpose) {
        if (purpose == UserPaymentPurpose.CUSTOMER_PAYOUT) {
            throw new InvalidOperationException("Customer payouts use the admin payout endpoint");
        }
    }

    private String requireReceiptUrl(String value) {
        String url = value.strip();
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException exception) {
            throw new InvalidInputException("Receipt URL is invalid");
        }

        return url;
    }

    private Query paymentQuery(
            String search, UserPaymentStatus status, UserPaymentDirection direction,
            UserPaymentPurpose purpose, String customerUserId
    ) {
        Query query = new Query();
        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }
        if (direction != null) {
            query.addCriteria(Criteria.where("direction").is(direction));
        }
        if (purpose != null) {
            query.addCriteria(Criteria.where("purpose").is(purpose));
        }
        if (customerUserId != null && !customerUserId.isBlank()) {
            query.addCriteria(Criteria.where("userId").is(customerUserId.strip()));
        }
        if (search != null && !search.isBlank()) {
            String term = search.strip();
            if (term.length() > 128) {
                throw new InvalidInputException("Payment search must not exceed 128 characters");
            }

            Pattern text = Pattern.compile(Pattern.quote(term), Pattern.CASE_INSENSITIVE);
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("id").is(term),
                    Criteria.where("userId").is(term),
                    Criteria.where("targetId").is(term),
                    Criteria.where("transferReference").regex(text),
                    Criteria.where("accountName").regex(text),
                    Criteria.where("bankName").regex(text)
            ));
        }
        return query;
    }

    private PaginatedData page(Query query, int page, int size) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) {
            throw new InvalidInputException("Invalid page or size");
        }

        long total = mongoTemplate.count(query, UserPayment.class);
        query.with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")))
                .skip((long) (page - 1) * size)
                .limit(size);

        List<UserPayment> payments = mongoTemplate.find(query, UserPayment.class);
        return utilsService.createPaginatedData(payments, page, size, total);
    }
}