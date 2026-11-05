package com.echaris.api.userauthmgt.userotp;






import com.echaris.api.utils.BaseEntity;
import lombok.EqualsAndHashCode;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@EqualsAndHashCode(callSuper = true)
@Document(collection = "user_otp")
public class UserOtp extends BaseEntity {

    private String userId;
    private String otp;
    private LocalDateTime expiresAt;
    private UserOtpType userOtpType;
    private boolean isActive;

    public UserOtp(String userId, String otp, LocalDateTime expiresAt, UserOtpType userOtpType, boolean isActive) {
        this.userId = userId;
        this.otp = otp;
        this.expiresAt = expiresAt;
        this.userOtpType = userOtpType;
        this.isActive = isActive;
    }

    public UserOtp() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getOtp() {
        return otp;
    }

    public void setOtp(String otp) {
        this.otp = otp;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public UserOtpType getUserOtpType() {
        return userOtpType;
    }

    public void setUserOtpType(UserOtpType userOtpType) {
        this.userOtpType = userOtpType;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }
}
