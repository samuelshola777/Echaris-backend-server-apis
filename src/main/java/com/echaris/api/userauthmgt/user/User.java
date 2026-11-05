package com.echaris.api.userauthmgt.user;


import com.echaris.api.utils.BaseEntity;
import com.echaris.api.utils.Gender;
import lombok.*;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.time.LocalDateTime;


@EqualsAndHashCode(callSuper = true)
@Data
@Document(collection = "user")
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User extends BaseEntity {
    private String firstName;
    private String lastName;
    @Indexed
    private String emailAddress;
    private String password;
    private String phoneNumber;
    private LocalDate dateOfBirth;
    private UserType userType;
    private boolean isActive;
    private String profilePictureUrl;
    private String profileColorCode;
    private boolean isDeleted;
    private boolean isVerified;
    private LocalDateTime verifiedAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime deletedAt;
    private LocalDateTime passwordChangedAt;
    private String age;
    private Gender gender;

    public User(String firstName, String lastName, String emailAddress, String phoneNumber, UserType userType,
                boolean isActive, String profilePictureUrl, String profileColorCode, String age) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.emailAddress = emailAddress;
        this.phoneNumber = phoneNumber;
        this.userType = userType;
        this.isActive = isActive;
        this.profilePictureUrl = profilePictureUrl;
        this.profileColorCode = profileColorCode;
        this.age = age;
    }

    public User(String firstName, String lastName, String emailAddress, String phoneNumber, UserType userType,
                boolean isActive, String age) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.emailAddress = emailAddress;
        this.phoneNumber = phoneNumber;
        this.userType = userType;
        this.isActive = isActive;
        this.age = age;
    }
}
