package com.echaris.api.marketplace;

import com.echaris.api.accountdetails.EcharisAccountDetails;
import com.echaris.api.address.Address;
import com.echaris.api.address.AddressType;
import com.echaris.api.admin.AdminService;
import com.echaris.api.commitment.CommitmentRules;
import com.echaris.api.customer.CustomerService;
import com.echaris.api.marketplace.request.MarketplaceFinancingRequest;
import com.echaris.api.marketplace.request.MarketplaceFulfillmentRequest;
import com.echaris.api.marketplace.request.MarketplaceFulfillmentStatusRequest;
import com.echaris.api.marketplace.response.MarketplacePaymentInstructionsResponse;
import com.echaris.api.marketplace.response.MarketplaceTermsResponse;
import com.echaris.api.product.Product;
import com.echaris.api.product.ProductService;
import com.echaris.api.product.ProductStockService;
import com.echaris.api.userPayment.UserPayment;
import com.echaris.api.userPayment.UserPaymentPurpose;
import com.echaris.api.utils.PaginatedData;
import com.echaris.api.utils.PaginationService;
import com.echaris.api.utils.exception.InvalidInputException;
import com.echaris.api.utils.exception.InvalidOperationException;
import com.echaris.api.utils.exception.ResourceExistsException;
import com.echaris.api.utils.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class MarketplaceFinancingService {

    private static final BigDecimal MINIMUM_CAPITAL = new BigDecimal("50000.00");

    private final MongoTemplate mongoTemplate;
    private final CustomerService customerService;
    private final AdminService adminService;
    private final ProductService productService;
    private final ProductStockService stockService;
    private final MarketplaceRateService rateService;
    private final MarketplaceReturnService returnService;
    private final MarketplaceNotificationService notificationService;
    private final PaginationService paginationService;

    @Value("${echaris.marketplace.agreement-version:1}")
    private String agreementVersion;

    @PostConstruct
    public void createIndexes() {
        var indexes = mongoTemplate.indexOps(MarketplaceFinancing.class);
        indexes.createIndex(new Index().on("userId", Sort.Direction.ASC).on("createdAt", Sort.Direction.DESC));
        indexes.createIndex(new Index().on("status", Sort.Direction.ASC).on("nextReturnAt", Sort.Direction.ASC));
    }

    public MarketplaceTermsResponse getTerms() {
        return new MarketplaceTermsResponse(
                agreementVersion, rateService.getConfiguredPercentage(), MINIMUM_CAPITAL,
                MarketplaceFinancing.PRODUCT_RELEASE_PERCENT, MarketplaceFinancing.CAPITAL_PAYOUT_PERCENT
        );
    }

    public MarketplaceFinancing createFinancing(MarketplaceFinancingRequest request) {
        String userId = customerService.getCurrentProfile().userId();

        if (!request.agreementAccepted() || !agreementVersion.equals(request.agreementVersion().strip())) {
            throw new InvalidInputException("Accept the current marketplace agreement");
        }

        int quantity = request.quantity().intValueExact();
        Product product = getProductWithStock(request.productId().strip(), quantity);
        BigDecimal capital = BigDecimal.valueOf(CommitmentRules.kobo(request.capital()), 2);
        if (capital.compareTo(MINIMUM_CAPITAL) < 0) {
            throw new InvalidInputException("Marketplace capital must be at least NGN 50,000");
        }

        MarketplaceFinancing financing = new MarketplaceFinancing();
        financing.setId(UUID.randomUUID().toString());
        financing.setUserId(userId);
        financing.setProductId(product.getId());
        financing.setProductName(product.getName());
        financing.setName(request.name().strip());
        financing.setQuantity(quantity);
        financing.setProductTotal(product.getPrice().multiply(BigDecimal.valueOf(quantity)));
        financing.setCapital(capital);
        financing.setFulfillment(buildFulfillment(request.fulfillment()));
        financing.setAgreementVersion(agreementVersion);
        financing.setAgreementAcceptedAt(LocalDateTime.now(ZoneOffset.UTC));
        return mongoTemplate.insert(financing);
    }

    public MarketplacePaymentInstructionsResponse getPaymentInstructions(String financingId) {
        MarketplaceFinancing financing = getUnpaidFinancing(financingId, customerService.getCurrentProfile().userId());
        getProductWithStock(financing.getProductId(), financing.getQuantity());
        rateService.getConfiguredPercentage();

        EcharisAccountDetails account = mongoTemplate.findById(EcharisAccountDetails.ACCOUNT_DETAILS_ID,
                EcharisAccountDetails.class);
        if (account == null) {
            throw new InvalidOperationException("Echaris payment account has not been configured");
        }
        return new MarketplacePaymentInstructionsResponse(financing.getId(), UserPaymentPurpose.MARKETPLACE_FINANCING,
                financing.getCapital(), account.getBankName(), account.getAccountName(), account.getAccountNumber());
    }

    public MarketplaceFinancing getUnpaidFinancing(String financingId, String userId) {
        MarketplaceFinancing financing = getCustomerFinancing(financingId, userId);

        if (financing.getStatus() != MarketplaceFinancingStatus.AWAITING_PAYMENT) {
            throw new InvalidOperationException("Marketplace financing is no longer awaiting payment");
        }
        return financing;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MarketplaceFinancing confirmFinancingPayment(UserPayment payment) {
        MarketplaceFinancing financing = getUnpaidFinancing(payment.getTargetId(), payment.getUserId());

        if (financing.getCapital().compareTo(payment.getAmount()) != 0) {
            throw new InvalidOperationException("Verified payment must match the marketplace capital amount");
        }

        getProductWithStock(financing.getProductId(), financing.getQuantity());
        BigDecimal monthlyRate = rateService.getConfiguredPercentage();
        stockService.reserveForMarketplace(financing.getProductId(), financing.getQuantity(),
                payment.getReviewedByUserId(), financing.getId());

        financing.setFundingPaymentId(payment.getId());
        financing.setApprovedAt(LocalDateTime.ofInstant(payment.getReviewedAt(), ZoneOffset.UTC));
        financing.setMonthlyRate(monthlyRate);
        financing.setStatus(MarketplaceFinancingStatus.ACCUMULATING);
        returnService.scheduleReturn(financing, 1);
        return saveFinancing(financing);
    }

    public MarketplaceFinancing getMyFinancing(String financingId) {
        return getCustomerFinancing(financingId, customerService.getCurrentProfile().userId());
    }

    public MarketplaceFinancing getAdminFinancing(String financingId) {
        adminService.getCurrentAdminProfile();
        return getFinancingById(financingId);
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
        return returnService.getReturns(getMyFinancing(financingId).getId(), page, size);
    }

    public PaginatedData getAdminReturns(String financingId, int page, int size) {
        return returnService.getReturns(getAdminFinancing(financingId).getId(), page, size);
    }

    public MarketplaceFinancing updateFulfillment(String financingId, MarketplaceFulfillmentRequest request) {
        MarketplaceFinancing financing = getMyFinancing(financingId);

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
        MarketplaceFinancing financing = getMyFinancing(financingId);

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

    private MarketplaceFinancing getFinancingById(String financingId) {
        MarketplaceFinancing financing = mongoTemplate.findById(financingId, MarketplaceFinancing.class);
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
        return paginationService.paginate(filter, MarketplaceFinancing.class, page, size,
                "createdAt", "DESC", Set.of("createdAt"));
    }
}