package com.echaris.api.userauthmgt.userotp;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserOtpRepository extends MongoRepository<UserOtp, String> {
    boolean existsByUserIdAndOtpAndUserOtpType(String userId, String otp,UserOtpType userOtpType);
    Optional<UserOtp> findByUserIdAndUserOtpType(String userId, UserOtpType userOtpType);
}
