package com.echaris.api.userPayment;

import com.echaris.api.commitment.Commitment;
import com.echaris.api.commitment.CommitmentRules;
import com.echaris.api.commitment.CommitmentService;
import com.echaris.api.commitment.CommitmentStatus;
import com.echaris.api.userPayment.request.UserPaymentApprovalRequest;
import com.echaris.api.userPayment.request.UserPaymentRequest;
import com.echaris.api.userauthmgt.user.User;
import com.echaris.api.userauthmgt.user.UserType;
import com.echaris.api.utils.PaginatedData;
import com.echaris.api.utils.UtilsService;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserPaymentService {

    private final MongoTemplate mongoTemplate;
    private final CommitmentService commitmentService;
    private final ManagedFileRepository managedFileRepository;
    private final UtilsService utilsService;

    @PostConstruct
    public void createIndexes() {
        var indexes = mongoTemplate.indexOps(UserPayment.class);
        indexes.createIndex(new Index()
                .on("bankCreditId", Sort.Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(new Document("bankCreditId", new Document("$type", "string"))))
                .named("uq_user_payment_bank_credit"));
        indexes.createIndex(new Index().on("userId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        indexes.createIndex(new Index().on("status", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
    }

    public UserPayment submit(UserPaymentRequest request) {
        String userId = requireCustomerId();
        requireCommitmentPurpose(request.purpose());
        Commitment commitment = commitmentService.get(request.targetId().strip());
        BigDecimal amount = BigDecimal.valueOf(CommitmentRules.kobo(request.amount()), 2);

        if (commitment.getStatus() != CommitmentStatus.AWAITING_PAYMENT || commitment.getFundedAt() != null) {
            throw new InvalidOperationException("This commitment is not awaiting payment");
        }
        if (amount.compareTo(commitment.getCapital()) != 0) {
            throw new InvalidInputException("The submitted amount must match the commitment amount");
        }

        UserPayment payment = new UserPayment();
        payment.setId(UUID.randomUUID().toString());
        payment.setUserId(userId);
        payment.setPurpose(request.purpose());
        payment.setTargetId(commitment.getId());
        payment.setAmount(amount);
        payment.setReceiptUrls(request.receiptUrls().stream().map(this::requireReceiptUrl).distinct().toList());
        String reference = request.transferReference();
        payment.setTransferReference(reference == null || reference.isBlank() ? null : reference.strip());
        return mongoTemplate.insert(payment);
    }

    public PaginatedData getMyPayments(int page, int size) {
        return page(Criteria.where("userId").is(requireCustomerId()), page, size);
    }

    public PaginatedData getAdminPayments(UserPaymentStatus status, int page, int size) {
        commitmentService.requireAdmin();
        return page(Criteria.where("status").is(status), page, size);
    }

    @Transactional
    public UserPayment approve(String paymentId, UserPaymentApprovalRequest request) {
        User admin = commitmentService.requireAdmin();
        UserPayment payment = requirePayment(paymentId);
        requireCommitmentPurpose(payment.getPurpose());
        String bankCreditId = request.bankCreditId().strip();
        BigDecimal verifiedAmount = BigDecimal.valueOf(CommitmentRules.kobo(request.verifiedAmount()), 2);
        Instant reviewedAt = Instant.now();
        if (request.paidAt().isAfter(reviewedAt)) {
            throw new InvalidInputException("Bank credit time cannot be in the future");
        }
        Instant paidAt = request.paidAt().truncatedTo(ChronoUnit.MILLIS);

        if (verifiedAmount.compareTo(payment.getAmount()) != 0) {
            throw new InvalidOperationException("The verified bank amount does not match the submitted amount");
        }
        if (payment.getStatus() == UserPaymentStatus.APPROVED  && bankCreditId.equals(payment.getBankCreditId()) && paidAt.equals(payment.getPaidAt())) {
            return payment;
        }
        requirePending(payment);

        payment.setStatus(UserPaymentStatus.APPROVED);
        payment.setBankCreditId(bankCreditId);
        payment.setPaidAt(paidAt);
        payment.setReviewedByUserId(admin.getId());
        payment.setReviewedAt(reviewedAt);
        payment.setUpdatedAt(LocalDateTime.now());
        UserPayment saved;
        try {
            saved = mongoTemplate.save(payment);
        } catch (DuplicateKeyException exception) {
            throw new ResourceExistsException("This bank credit has already been applied to a payment");
        }

        commitmentService.confirmManualFunding(
                saved.getTargetId(), saved.getUserId(), saved.getAmount(),
                saved.getId(), paidAt, admin.getId()
        );
        return saved;
    }

    public UserPayment reject(String paymentId) {
        User admin = commitmentService.requireAdmin();
        UserPayment payment = requirePayment(paymentId);
        if (payment.getStatus() == UserPaymentStatus.REJECTED) {
            return payment;
        }
        requirePending(payment);
        payment.setStatus(UserPaymentStatus.REJECTED);
        payment.setReviewedByUserId(admin.getId());
        payment.setReviewedAt(Instant.now());
        payment.setUpdatedAt(LocalDateTime.now());
        return mongoTemplate.save(payment);
    }

    private String requireCustomerId() {
        User user = commitmentService.currentUser();
        if (user.getUserType() != UserType.CUSTOMER) {
            throw new UnauthorizedException("Customer access is required");
        }
        return user.getId();
    }

    private UserPayment requirePayment(String paymentId) {
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

    private void requireCommitmentPurpose(UserPaymentPurpose purpose) {
        if (purpose != UserPaymentPurpose.COMMITMENT) {
            throw new InvalidOperationException("Manual order payments are not available yet");
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
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || !managedFileRepository.existsByFileUrl(url)) {
            throw new InvalidInputException("Each receipt must be an uploaded HTTPS file URL");
        }
        return url;
    }

    private PaginatedData page(Criteria criteria, int page, int size) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) {
            throw new InvalidInputException("Invalid page or size");
        }
        long total = mongoTemplate.count(Query.query(criteria), UserPayment.class);
        Query query = Query.query(criteria)
                .with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")))
                .skip((long) (page - 1) * size)
                .limit(size);
        List<UserPayment> payments = mongoTemplate.find(query, UserPayment.class);
        return utilsService.createPaginatedData(payments, page, size, total);
    }
}