package com.echaris.api.customer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface CustomerRepository extends MongoRepository<Customer, String> {

    Optional<Customer> findByIdAndDeletedFalse(String id);

    Optional<Customer> findByUserIdAndDeletedFalse(String userId);

    Page<Customer> findAllByDeletedFalse(Pageable pageable);

    boolean existsByUserId(String userId);

    boolean existsByCustomerNumber(String customerNumber);

    long countByDeletedFalse();

    long countByDeletedFalseAndActiveTrue();

    long countByDeletedFalseAndActiveFalse();

    long countByDeletedFalseAndCreatedAtBetween(
            LocalDateTime start,
            LocalDateTime end
    );
}