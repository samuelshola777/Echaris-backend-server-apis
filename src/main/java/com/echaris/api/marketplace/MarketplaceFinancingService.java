package com.echaris.api.marketplace;

import com.echaris.api.address.Address;
import com.echaris.api.address.AddressType;
import com.echaris.api.admin.AdminService;
import com.echaris.api.commitment.CommitmentRules;
import com.echaris.api.customer.CustomerService;
import com.echaris.api.marketplace.request.MarketplaceFinancingRequest;
import com.echaris.api.marketplace.request.MarketplaceFulfillmentRequest;
import com.echaris.api.marketplace.request.MarketplaceFulfillmentStatusRequest;
import com.echaris.api.marketplace.response.MarketplaceFinancingResponse;
import com.echaris.api.product.Product;
import com.echaris.api.product.ProductRepository;
import com.echaris.api.product.ProductService;
import com.echaris.api.product.ProductStockService;
import com.echaris.api.userPayment.UserPayment;
import com.echaris.api.userPayment.UserPaymentDirection;
import com.echaris.api.userPayment.UserPaymentPurpose;
import com.echaris.api.userPayment.UserPaymentStatus;
import com.echaris.api.utils.PaginatedData;
import com.echaris.api.utils.PaginationService;
import com.echaris.api.utils.exception.InvalidInputException;
import com.echaris.api.utils.exception.InvalidOperationException;
import com.echaris.api.utils.exception.ResourceExistsException;
import com.echaris.api.utils.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class MarketplaceFinancingService {

    private final MongoTemplate mongoTemplate;
    private final CustomerService customerService;
    private final AdminService adminService;
    private final ProductService productService;
    private final ProductStockService stockService;
    private final MarketplaceRateService rateService;
    private final MarketplaceReturnService returnService;
    private final MarketplaceNotificationService notificationService;
    private final PaginationService paginationService;
    private final MarketplaceFinancingRepository marketplaceFinancingRepository;
    private final ProductRepository productRepository;

    @PostConstruct
    public void createIndexes() {
        var indexes = mongoTemplate.indexOps(MarketplaceFinancing.class);
        indexes.createIndex(new Index().on("userId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        indexes.createIndex(new Index().on("status", Sort.Direction.ASC).on("nextReturnAt", Sort.Direction.ASC));
    }

    public MarketplaceFinancing createFinancing(MarketplaceFinancingRequest request) {
        if (!request.agreementAccepted()) {
            throw new InvalidInputException("Accept the current marketplace agreement");
        }
        String userId = customerService.getCurrentProfile().userId();
        boolean alreadyActive = marketplaceFinancingRepository.existsByProductIdAndUserIdAndCompletedFalse(request.productId(), userId);
        if (alreadyActive) {
            throw new ResourceExistsException(
                    "You already have an active marketplace financing for this product");
        }
        int quantity = request.quantity().intValueExact();
        Product product = getProductWithStock(request.productId(), quantity);
        BigDecimal capital = BigDecimal.valueOf(CommitmentRules.kobo(request.capital()), 2);

        MarketplaceFinancing financing = new MarketplaceFinancing();
        financing.setUserId(userId);
        financing.setProductId(product.getId());
        financing.setName(request.name().strip());
        financing.setProductQuantity(quantity);
        financing.setCapital(capital);
        financing.setAgreementAccepted(request.agreementAccepted());
        financing.setFulfillment(buildFulfillment(request.fulfillment()));
        financing.setAgreementAcceptedAt(LocalDateTime.now(ZoneOffset.UTC));
        return marketplaceFinancingRepository.save(financing);
    }

    public PaginatedData getAdminFinancings(
            MarketplaceFinancingStatus status,
            String search,
            int page,
            int size
    ) {
        adminService.getCurrentAdminProfile();

        Query query = new Query();

        if (status != null) {
            query.addCriteria(Criteria.where("status").is(status));
        }

        if (search != null && !search.isBlank()) {
            String regex = java.util.regex.Pattern.quote(search.strip());
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("name").regex(regex, "i"),
                    Criteria.where("productName").regex(regex, "i"),
                    Criteria.where("productId").regex(regex, "i"),
                    Criteria.where("userId").regex(regex, "i"),
                    Criteria.where("id").regex(regex, "i")
            ));
        }

        return getFinancingPage(query, page, size);
    }

    @Transactional
    public UserPayment adminUpdatesMarketFinancingPaymentStatus(
            String userPaymentId,
            UserPaymentStatus userPaymentStatus,
            String reasonForRejection
    ) {
        String adminId = adminService.getCurrentAdminProfile().userId();
        boolean approved = userPaymentStatus == UserPaymentStatus.APPROVED;

        if (!approved && userPaymentStatus != UserPaymentStatus.REJECTED) {
            throw new InvalidInputException("Payment status must be APPROVED or REJECTED");
        }

        UserPayment payment = mongoTemplate.findById(
                requireText(userPaymentId, "Payment ID"), UserPayment.class
        );
        if (payment == null) {
            throw new ResourceNotFoundException("Payment submission not found");
        }
        if (payment.getPurpose() != UserPaymentPurpose.MARKETPLACE_FINANCING
                || payment.getDirection() != UserPaymentDirection.INCOMING) {
            throw new InvalidOperationException("Payment must be an incoming marketplace payment");
        }

        if (!approved) {
            reasonForRejection = requireText(reasonForRejection, "Rejection reason");

        }

        if (payment.getStatus() == userPaymentStatus) {
            return payment;
        }
        if (payment.getStatus() != UserPaymentStatus.PENDING) {
            throw new InvalidOperationException("Only pending payments can be reviewed");
        }

        MarketplaceFinancing financing = getUnpaidFinancing(
                payment.getTargetId(), payment.getUserId()
        );
        if (financing.getFundingPaymentId() != null
                && !payment.getId().equals(financing.getFundingPaymentId())) {
            throw new InvalidOperationException("Payment does not belong to this marketplace financing");
        }
        if (payment.getAmount() == null || financing.getCapital() == null
                || payment.getAmount().compareTo(financing.getCapital()) != 0) {
            throw new InvalidInputException("Payment amount must match the marketplace capital");
        }

        Instant reviewedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        payment.setStatus(userPaymentStatus);
        payment.setReviewedByUserId(adminId);
        payment.setReviewedAt(reviewedAt);
        payment.setUpdatedAt(LocalDateTime.now());
        payment.setReasonForRejection(approved ? null : reasonForRejection);

        UserPayment saved = mongoTemplate.save(payment);
        if (approved) {
            fundMarketplaceFinancing(financing, saved);
        }
        notificationService.sendPaymentDecision(financing, saved);
        return saved;
    }
    private void fundMarketplaceFinancing(MarketplaceFinancing financing, UserPayment payment) {
        Product product = getProductWithStock(financing.getProductId(), financing.getProductQuantity());
        BigDecimal monthlyRate = rateService.getConfiguredPercentage();
        stockService.reserveForMarketplace(financing.getProductId(), financing.getProductQuantity(),
                payment.getReviewedByUserId(), financing.getId());


        financing.setFundingPaymentId(payment.getId());
        financing.setApprovedAt(LocalDateTime.ofInstant(payment.getReviewedAt(), ZoneOffset.UTC));
        financing.setMonthlyRate(monthlyRate);
        financing.setStatus(MarketplaceFinancingStatus.ACCUMULATING);
        returnService.scheduleReturn(financing, 1);
        saveFinancing(financing);
    }

    @Transactional
    public MarketplaceFinancing makePaymentForMarketFinancing(String marketFinancingId, String userPaymentId) {
        String userId = customerService.getCurrentProfile().userId();
        MarketplaceFinancing financing = getUnpaidFinancing(marketFinancingId, userId);
        UserPayment payment = mongoTemplate.findById(userPaymentId, UserPayment.class);

        if (payment == null) {
            throw new ResourceNotFoundException("Payment submission not found");
        }
        if (payment.getPurpose() != UserPaymentPurpose.MARKETPLACE_FINANCING
                || payment.getDirection() != UserPaymentDirection.INCOMING
                || !userId.equals(payment.getUserId())
                || !financing.getId().equals(payment.getTargetId())) {
            throw new InvalidOperationException("Payment does not belong to this marketplace financing");
        }
        if (payment.getStatus() != UserPaymentStatus.PENDING) {
            throw new InvalidOperationException("Only pending payments can be submitted for review");
        }
        if (payment.getAmount() == null || financing.getCapital() == null
                || payment.getAmount().compareTo(financing.getCapital()) != 0) {
            throw new InvalidInputException("Payment amount must match the marketplace capital");
        }

        String currentPaymentId = financing.getFundingPaymentId();
        if (payment.getId().equals(currentPaymentId)) {
            return financing;
        }
        if (currentPaymentId != null) {
            UserPayment currentPayment = mongoTemplate.findById(currentPaymentId, UserPayment.class);
            if (currentPayment == null || currentPayment.getStatus() != UserPaymentStatus.REJECTED) {
                throw new ResourceExistsException("This marketplace financing already has a payment for review");
            }
        }

        getProductWithStock(financing.getProductId(), financing.getProductQuantity());

        Query query = Query.query(Criteria.where("id").is(financing.getId())
                .and("status").is(MarketplaceFinancingStatus.AWAITING_PAYMENT)
                .and("fundingPaymentId").is(currentPaymentId));
        Update update = new Update().set("fundingPaymentId", payment.getId())
                .set("updatedAt", LocalDateTime.now());

        MarketplaceFinancing saved = mongoTemplate.findAndModify(
                query, update, FindAndModifyOptions.options().returnNew(true), MarketplaceFinancing.class);
        if (saved == null) {
            throw new ResourceExistsException("Marketplace financing payment changed. Reload before trying again");
        }

        notificationService.sendPaymentAwaitingApproval(saved, payment);
        return saved;
    }

    public MarketplaceFinancing getUnpaidFinancing(String financingId, String userId) {
        MarketplaceFinancing financing = getCustomerFinancing(financingId, userId);
        if (financing.getStatus() != MarketplaceFinancingStatus.AWAITING_PAYMENT) {
            throw new InvalidOperationException("Marketplace financing is no longer awaiting payment");
        }
        return financing;
    }



    public MarketplaceFinancing getFinancingById(String financingId) {
        return marketplaceFinancingRepository.findById(financingId)
                .orElseThrow(() -> new ResourceNotFoundException("Marketplace financing not found"));
    }

    public MarketplaceFinancingResponse getMyFinancing(String financingId) {
        return buildFinancingResponse(getCurrentCustomerFinancing(financingId));
    }

    public MarketplaceFinancingResponse getAdminFinancing(String financingId) {
        adminService.getCurrentAdminProfile();
        return buildFinancingResponse(getFinancingById(financingId));
    }

    private MarketplaceFinancingResponse buildFinancingResponse(MarketplaceFinancing financing) {
        String productId = financing.getProductId();
        Product product = productId == null ? null : productRepository.findById(productId).orElse(null);
        return MarketplaceFinancingResponse.from(financing, product);
    }

    private MarketplaceFinancing getCurrentCustomerFinancing(String financingId) {
        return getCustomerFinancing(financingId, customerService.getCurrentProfile().userId());
    }

    public PaginatedData getMyFinancings(int page, int size) {
        return getFinancingPage(Query.query(Criteria.where("userId").is(customerService.getCurrentProfile().userId())), page, size);
    }

    public PaginatedData getAdminFinancings(MarketplaceFinancingStatus status, int page, int size) {
        adminService.getCurrentAdminProfile();
        Query query = status == null ? new Query() : Query.query(Criteria.where("status").is(status));
        return getFinancingPage(query, page, size);
    }

    public PaginatedData getMyReturns(String financingId, int page, int size) {
        return returnService.getReturns(getCurrentCustomerFinancing(financingId).getId(), page, size);
    }

    public PaginatedData getAdminReturns(String financingId, int page, int size) {
        adminService.getCurrentAdminProfile();
        return returnService.getReturns(getFinancingById(financingId).getId(), page, size);
    }

    @Transactional
    public MarketplaceFinancing updateFulfillment(String financingId, MarketplaceFulfillmentRequest request) {
        MarketplaceFinancing financing = getCurrentCustomerFinancing(financingId);

        if (!financing.getFulfillmentStatus().allowsCustomerUpdate()) {
            throw new ResourceExistsException("Fulfillment details are locked because preparation has started");
        }

        MarketplaceFulfillment updated = buildFulfillment(request);

        if (updated.equals(financing.getFulfillment())) {
            return financing;
        }
        financing.setFulfillment(updated);
        return saveFinancing(financing);
    }

    @Transactional
    public MarketplaceFinancing updateFulfillmentStatus(String financingId, MarketplaceFulfillmentStatusRequest request) {
        String adminId = adminService.getCurrentAdminProfile().userId();
        MarketplaceFinancing financing = getFinancingById(financingId);
        MarketplaceFulfillmentStatus previous = financing.getFulfillmentStatus();
        MarketplaceFulfillmentStatus next = request.status();

        if (previous == next) {
            return financing;
        }
        if (!previous.canChangeTo(next, financing.getFulfillment().type())) {
            throw new InvalidOperationException("Invalid marketplace fulfillment status change");
        }

        financing.setFulfillmentStatus(next);
        financing.getFulfillmentHistory().add(new MarketplaceFulfillmentHistory(previous, next, adminId, Instant.now()));
        MarketplaceFinancing saved = saveFinancing(financing);
        notificationService.sendFulfillmentUpdate(saved);
        return saved;
    }

    @Transactional
    public MarketplaceFinancing requestCapitalPayout(String financingId) {
        MarketplaceFinancing financing = getCurrentCustomerFinancing(financingId);

        if (financing.getStatus() != MarketplaceFinancingStatus.MATURED) {
            throw new InvalidOperationException("Capital can be requested once returns reach the product total");
        }
        if (financing.getCapitalRequestedAt() != null) {
            return financing;
        }
        financing.setCapitalRequestedAt(LocalDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MILLIS));
        MarketplaceFinancing saved = saveFinancing(financing);
        notificationService.sendCapitalRequest(saved);
        return saved;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordCapitalPayout(UserPayment payment) {
        MarketplaceFinancing financing = getCustomerFinancing(payment.getTargetId(), payment.getUserId());
        LocalDateTime requestedAt = financing.getCapitalRequestedAt();
        LocalDateTime paidAt = LocalDateTime.ofInstant(payment.getPaidAt(), ZoneOffset.UTC);
        if (financing.getStatus() != MarketplaceFinancingStatus.MATURED || requestedAt == null
                || paidAt.isBefore(requestedAt) || financing.getCapitalPayoutId() != null) {
            throw new InvalidOperationException("Marketplace capital must be requested and unpaid at the transfer time");
        }
        if (financing.getCapital().compareTo(payment.getAmount()) != 0) {
            throw new InvalidInputException("Payout amount must match the original marketplace capital");
        }

        financing.setCapitalPayoutId(payment.getId());
        financing.setCapitalPaidAt(paidAt);
        saveFinancing(financing);
    }

    public MarketplaceFinancing getCustomerFinancing(String financingId, String userId) {
        MarketplaceFinancing financing = mongoTemplate.findOne(
                Query.query(Criteria.where("id").is(financingId).and("userId").is(userId)),
                MarketplaceFinancing.class);
        if (financing == null) {
            throw new ResourceNotFoundException("Marketplace financing not found");
        }
        return financing;
    }

    private MarketplaceFinancing saveFinancing(MarketplaceFinancing financing) {
        financing.setUpdatedAt(LocalDateTime.now());
        return mongoTemplate.save(financing);
    }

    private Product getProductWithStock(String productId, int quantity) {
        Product product = productService.catalogProduct(productId);

        if (quantity < 1) {
            throw new InvalidInputException("Marketplace financing quantity must be positive");
        }
        if (quantity > product.getStockQuantity()) {
            throw new ResourceExistsException("Requested quantity exceeds available stock");
        }
        if (product.getPrice() == null || product.getPrice().signum() <= 0) {
            throw new ResourceNotFoundException("Product is unavailable");
        }
        return product;
    }

    private MarketplaceFulfillment buildFulfillment(MarketplaceFulfillmentRequest request) {
        return switch (request.type()) {
            case DELIVERY -> buildDelivery(request);
            case PICKUP -> buildPickup(request.pickupLocationId());
            default -> throw new InvalidInputException("Select pickup or delivery for marketplace fulfillment");
        };
    }

    private MarketplaceFulfillment buildDelivery(MarketplaceFulfillmentRequest request) {
        String address = requireText(request.deliveryAddress(), "Delivery address");
        String phone = requireText(request.phoneNumber(), "Delivery phone number");
        if (!phone.matches("\\+?[0-9][0-9 ()-]{5,30}[0-9]")) {
            throw new InvalidInputException("Delivery phone number is invalid");
        }
        return new MarketplaceFulfillment(AddressType.DELIVERY, null, address, phone);
    }

    private MarketplaceFulfillment buildPickup(String pickupLocationId) {
        String locationId = requireText(pickupLocationId, "Pickup location");
        Address pickup = mongoTemplate.findOne(Query.query(Criteria.where("id").is(locationId)
                .and("addressType").is(AddressType.PICKUP)), Address.class);
        if (pickup == null) {
            throw new ResourceNotFoundException("Pickup location not found");
        }
        String address = Stream.of(pickup.getAddressLine(), pickup.getAddressLine2(), pickup.getCity(),
                        pickup.getState(), pickup.getCountry(), pickup.getPostalCode())
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .collect(Collectors.joining(", "));
        return new MarketplaceFulfillment(AddressType.PICKUP, pickup.getId(), address, null);
    }

    private String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new InvalidInputException(label + " is required");
        }
        return value.strip();
    }

    private PaginatedData getFinancingPage(Query filter, int page, int size) {
        PaginatedData result = paginationService.paginate(filter, MarketplaceFinancing.class, page, size,
                "createdAt", "DESC", Set.of("createdAt"));
        List<MarketplaceFinancing> financings = ((List<?>) result.data()).stream()
                .map(MarketplaceFinancing.class::cast)
                .toList();
        List<String> productIds = financings.stream()
                .map(MarketplaceFinancing::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        Map<String, Product> productsById = new HashMap<>();
        if (!productIds.isEmpty()) {
            productRepository.findAllById(productIds)
                    .forEach(product -> productsById.put(product.getId(), product));
        }

        List<MarketplaceFinancingResponse> responses = financings.stream()
                .map(financing -> MarketplaceFinancingResponse.from(
                        financing, productsById.get(financing.getProductId())))
                .toList();
        return new PaginatedData(result.totalPage(), result.numberOfElements(), result.totalElements(), responses);
    }
}