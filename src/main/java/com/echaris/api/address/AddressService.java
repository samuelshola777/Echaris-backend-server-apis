package com.echaris.api.address;

import com.echaris.api.address.request.AddressRequest;
import com.echaris.api.address.response.AddressResponse;
import com.echaris.api.admin.AdminService;
import com.echaris.api.customer.CustomerService;
import com.echaris.api.userauthmgt.user.User;
import com.echaris.api.userauthmgt.user.UserService;
import com.echaris.api.utils.exception.InvalidInputException;
import com.echaris.api.utils.exception.InvalidOperationException;
import com.echaris.api.utils.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.index.PartialIndexFilter;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Validated
@RequiredArgsConstructor
public class AddressService {

    private static final String LEGACY_USER_INDEX = "uq_address_user_id";

    private final MongoTemplate mongoTemplate;
    private final UserService userService;
    private final CustomerService customerService;
    private final AdminService adminService;

    @PostConstruct
    public void ensureUserAddressIndex() {
        var indexes = mongoTemplate.indexOps(Address.class);

        // Keep delivery owners unique while allowing pickup addresses with no owner.
        indexes.createIndex(new Index()
                .on("userId", Sort.Direction.ASC)
                .unique()
                .partial(PartialIndexFilter.of(new Document("userId", new Document("$type", "string"))))
                .named("uq_address_delivery_owner"));

        if (indexes.getIndexInfo().stream().anyMatch(index -> LEGACY_USER_INDEX.equals(index.getName()))) {
            try {
                indexes.dropIndex(LEGACY_USER_INDEX);
            } catch (DataAccessException exception) {
                if (indexes.getIndexInfo().stream().anyMatch(index -> LEGACY_USER_INDEX.equals(index.getName()))) {
                    throw exception;
                }
            }
        }
    }

    public AddressResponse createOrUpdateAddress(@Valid AddressRequest request) {
        if (request == null || request.addressType() == null) {
            throw new InvalidInputException("Address type is required");
        }

        boolean admin = isAdmin();

        if (request.addressType() == AddressType.PICKUP) {
            if (!admin) {
                throw new InvalidOperationException("Only admins can create pickup addresses");
            }

            if (request.userId() != null && !request.userId().isBlank()) {
                throw new InvalidInputException("Pickup addresses do not take a user ID");
            }

            Address pickup = Address.builder()
                    .addressType(AddressType.PICKUP)
                    .country(clean(request.country()))
                    .state(clean(request.state()))
                    .city(clean(request.city()))
                    .addressLine(clean(request.addressLine()))
                    .addressLine2(clean(request.addressLine2()))
                    .postalCode(clean(request.postalCode()))
                    .build();

            return AddressResponse.from(mongoTemplate.insert(pickup));
        }

        String userId = deliveryOwner(request.userId(), admin);
        return saveDeliveryAddress(userId, request);
    }

    public List<AddressResponse> getAddresses(AddressType addressType, int page, int size) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) {
            throw new InvalidInputException("Page must be between 1 and 10000, and size between 1 and 100");
        }

        Query query = new Query();

        boolean admin = isAdmin();
        Criteria delivery = admin
                ? Criteria.where("addressType").in(AddressType.DELIVERY, null)
                : deliveryAddress(customerService.getCurrentProfile().userId());

        if (addressType == AddressType.PICKUP) {
            query.addCriteria(pickupAddress());
        } else if (addressType == AddressType.DELIVERY) {
            query.addCriteria(delivery);
        } else if (!admin) {
            query.addCriteria(new Criteria().orOperator(pickupAddress(), delivery));
        }

        query.with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id")))
                .skip((long) (page - 1) * size)
                .limit(size);

        return mongoTemplate.find(query, Address.class).stream()
                .map(AddressResponse::from)
                .toList();
    }

    public AddressResponse getAddressByUserId(String userId) {
        String owner = deliveryOwner(userId, isAdmin());
        Address address = mongoTemplate.findOne(Query.query(deliveryAddress(owner)), Address.class);

        if (address == null) {
            throw new ResourceNotFoundException("This user does not have an address");
        }

        return AddressResponse.from(address);
    }

    public void deleteAddressByUserId(String userId) {
        String owner = deliveryOwner(userId, isAdmin());
        var result = mongoTemplate.remove(Query.query(deliveryAddress(owner)), Address.class);

        if (result.getDeletedCount() == 0) {
            throw new ResourceNotFoundException("This user does not have an address");
        }
    }

    private AddressResponse saveDeliveryAddress(String userId, AddressRequest request) {
        Query query = Query.query(deliveryAddress(userId));
        LocalDateTime now = LocalDateTime.now();
        Update update = new Update()
                .set("addressType", AddressType.DELIVERY)
                .set("country", clean(request.country()))
                .set("state", clean(request.state()))
                .set("city", clean(request.city()))
                .set("addressLine", clean(request.addressLine()))
                .set("addressLine2", clean(request.addressLine2()))
                .set("postalCode", clean(request.postalCode()))
                .set("updatedAt", now)
                .setOnInsert("userId", userId)
                .setOnInsert("createdAt", now);

        Address address;

        try {
            address = mongoTemplate.findAndModify(
                    query, update, FindAndModifyOptions.options().upsert(true).returnNew(true), Address.class
            );
        } catch (DuplicateKeyException exception) {
            // A concurrent request may have inserted this customer's delivery address.
            address = mongoTemplate.findAndModify(
                    query, update, FindAndModifyOptions.options().returnNew(true), Address.class
            );
            if (address == null) {
                throw exception;
            }
        }

        if (address == null) {
            throw new IllegalStateException("Address could not be saved");
        }

        return AddressResponse.from(address);
    }

    private String deliveryOwner(String requestedUserId, boolean admin) {
        if (admin) {
            return requireExistingUser(requestedUserId);
        }

        String owner = customerService.getCurrentProfile().userId();

        if (requestedUserId != null && !requestedUserId.isBlank()
                && !owner.equalsIgnoreCase(requestedUserId.trim())) {
            throw new InvalidOperationException("You can only manage your own delivery address");
        }

        return owner;
    }

    private String requireExistingUser(String userId) {
        if (userId == null || !ObjectId.isValid(userId.trim())) {
            throw new InvalidInputException("A valid user ID is required");
        }

        User user = userService.findById(new ObjectId(userId.trim()).toHexString());

        if (user.isDeleted()) {
            throw new ResourceNotFoundException("User not found");
        }

        return user.getId();
    }

    private boolean isAdmin() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = authentication != null && authentication.isAuthenticated()
                && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));

        if (admin) {
            adminService.getCurrentAdminProfile();
        }

        return admin;
    }

    private Criteria pickupAddress() {
        return Criteria.where("addressType").is(AddressType.PICKUP);
    }

    private Criteria deliveryAddress(String userId) {
        return Criteria.where("userId").is(userId)
                .and("addressType").in(AddressType.DELIVERY, null);
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }
}