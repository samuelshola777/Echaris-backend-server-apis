package com.echaris.api.userauthmgt.user;

import jakarta.validation.constraints.NotBlank;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
public interface UserRepository extends MongoRepository<User, String> {

    boolean existsByEmailAddressIgnoreCase(String emailAddress);
    Optional<User> findByEmailAddressIgnoreCaseAndUserTypeAndIsDeleted(String emailAddress, UserType userType, boolean isDeleted);
    Optional<User> findByIdAndUserType(String id, UserType userType);
    Optional<User> findByEmailAddressIgnoreCaseAndUserType(String emailAddress, UserType userType);
    List<User> findAllByUserTypeAndIsDeleted(UserType userType, boolean isDeleted);

    Optional<User> findByEmailAddressIgnoreCaseAndIsDeleted(@NotBlank String email, boolean b);

    Optional<User> findByEmailAddress(String superAdminEmail);

    Set<String> findEmailAddressesByUserTypeAndIsDeleted(UserType userType, boolean b);
}
