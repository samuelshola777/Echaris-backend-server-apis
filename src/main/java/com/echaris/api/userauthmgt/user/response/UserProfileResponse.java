package com.echaris.api.userauthmgt.user.response;

import com.echaris.api.userauthmgt.user.User;
import com.echaris.api.utils.Gender;
import lombok.Builder;

@Builder
public record UserProfileResponse(

        String userId,
        String firstName,
        String lastName,
        String emailAddress,
        String phoneNumber,
        String profilePictureUrl,
        String profileColorCode,    
        Gender gender
) {

    public static UserProfileResponse fromUser(User user) {
        return UserProfileResponse.builder()
                .userId(user.getId())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .emailAddress(user.getEmailAddress())
                .phoneNumber(user.getPhoneNumber())
                .profilePictureUrl(user.getProfilePictureUrl())
                .profileColorCode(user.getProfileColorCode())
                .gender(user.getGender() == null ? Gender.MALE : user.getGender())
                .build();
    }

}
