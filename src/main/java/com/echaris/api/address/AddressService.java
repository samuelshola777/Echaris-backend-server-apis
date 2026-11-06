package com.echaris.api.address;

import com.echaris.api.address.request.AddressRequest;
import com.echaris.api.address.response.AddressResponse;
import com.echaris.api.userauthmgt.user.User;
import com.echaris.api.userauthmgt.user.UserService;
import com.echaris.api.utils.exception.InvalidInputException;
import com.echaris.api.utils.exception.ResourceNotFoundException;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Validated
@RequiredArgsConstructor
public class AddressService {

    private final MongoTemplate mongoTemplate;
    private final UserService userService;

    @PostConstruct
    public void ensureUserAddressIndex() {
        mongoTemplate.indexOps(Address.class)
                .createIndex(
                        new Index()
                                .on("userId", Sort.Direction.ASC)
                                .unique()
                                .named("uq_address_user_id")
                );
    }

    public AddressResponse createOrUpdateAddress(
            @Valid AddressRequest request
    ) {
        if (request == null) {
            throw new InvalidInputException(
                    "Request body is required"
            );
        }

        String userId = requireExistingUser(request.userId());

        Query query = userAddressQuery(userId);
        LocalDateTime now = LocalDateTime.now();

        Update update = new Update()
                .set("country", clean(request.country()))
                .set("state", clean(request.state()))
                .set("city", clean(request.city()))
                .set("addressLine", clean(request.addressLine()))
                .set("addressLine2", clean(request.addressLine2()))
                .set("postalCode", clean(request.postalCode()))
                .set("updatedAt", now)
                .setOnInsert("userId", userId)
                .setOnInsert("createdAt", now);

        Address savedAddress;

        try {
            savedAddress = mongoTemplate.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options()
                            .upsert(true)
                            .returnNew(true),
                    Address.class
            );
        } catch (DuplicateKeyException exception) {
            // Another request may have created this user's address.
            // Update that record while preserving the unique owner.
            savedAddress = mongoTemplate.findAndModify(
                    query,
                    update,
                    FindAndModifyOptions.options()
                            .returnNew(true),
                    Address.class
            );

            if (savedAddress == null) {
                throw exception;
            }
        }

        if (savedAddress == null) {
            throw new IllegalStateException(
                    "Address could not be saved"
            );
        }

        return AddressResponse.from(savedAddress);
    }

    public AddressResponse getAddressByUserId(String userId) {
        String validUserId = requireExistingUser(userId);

        Address address = mongoTemplate.findOne(
                userAddressQuery(validUserId),
                Address.class
        );

        if (address == null) {
            throw new ResourceNotFoundException(
                    "This user does not have an address"
            );
        }

        return AddressResponse.from(address);
    }

    public List<AddressResponse> getAddresses(int page, int size) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) {
            throw new InvalidInputException(
                    "Page must be between 1 and 10000, "
                            + "and size between 1 and 100"
            );
        }

        Query query = new Query()
                .with(Sort.by(
                        Sort.Order.desc("createdAt"),
                        Sort.Order.asc("id")
                ))
                .skip((long) (page - 1) * size)
                .limit(size);

        return mongoTemplate.find(query, Address.class)
                .stream()
                .map(AddressResponse::from)
                .toList();
    }

    public void deleteAddressByUserId(String userId) {
        String validUserId = requireExistingUser(userId);

        var result = mongoTemplate.remove(
                userAddressQuery(validUserId),
                Address.class
        );

        if (result.getDeletedCount() == 0) {
            throw new ResourceNotFoundException(
                    "This user does not have an address"
            );
        }
    }

    private String requireExistingUser(String userId) {
        if (userId == null
                || !ObjectId.isValid(userId.trim())) {
            throw new InvalidInputException(
                    "A valid user ID is required"
            );
        }

        String validUserId =
                new ObjectId(userId.trim()).toHexString();

        User user = userService.findById(validUserId);

        if (user.isDeleted()) {
            throw new ResourceNotFoundException(
                    "User not found"
            );
        }

        return user.getId();
    }

    private Query userAddressQuery(String userId) {
        return Query.query(
                Criteria.where("userId").is(userId)
        );
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }
}