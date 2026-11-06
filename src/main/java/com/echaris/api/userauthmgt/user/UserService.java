package com.echaris.api.userauthmgt.user;

import com.echaris.api.admin.request.AdminUpdateRequest;
import com.echaris.api.userauthmgt.user.request.*;
import com.echaris.api.userauthmgt.user.request.UserVerifyOtpRequest;
import com.echaris.api.userauthmgt.user.response.UserCreateResponse;
import com.echaris.api.userauthmgt.user.response.UserForgotPasswordResponse;
import com.echaris.api.userauthmgt.user.response.UserResponse;
import com.echaris.api.userauthmgt.userotp.UserOtpService;
import com.echaris.api.userauthmgt.userotp.UserOtpType;
import com.echaris.api.utils.ApiResponseMessages;
import com.echaris.api.utils.Gender;
import com.echaris.api.utils.appsecurity.JwtService;
import com.echaris.api.utils.emailsenderservice.EmailSenderService;
import com.echaris.api.utils.exception.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final MongoTemplate mongoTemplate;
	private final UserOtpService userOtpService;

	@Qualifier("mailgunEmailSenderServiceImpl")
	private final EmailSenderService emailSenderService;

	private final BCryptPasswordEncoder bCryptPasswordEncoder =
			new BCryptPasswordEncoder();

	private final PasswordEncoder passwordEncoder =
			new BCryptPasswordEncoder();

	private final JwtService jwtService;

	/**
	 * Updates the provided user's information in the database.
	 *
	 * @param existingUser the user entity to be updated
	 */
	public User updateUser(User existingUser) {
		existingUser.setUpdatedAt(LocalDateTime.now());

		existingUser.setAge(
				existingUser.getDateOfBirth() != null
						? calculateAge(existingUser.getDateOfBirth())
						: ""
		);

		return userRepository.save(existingUser);
	}

	/**
	 * Creates a new user account based on the provided request.
	 */
	public UserCreateResponse createUser(
			UserCreateRequest userCreateRequest
	) {
		if (userRepository.existsByEmailAddressIgnoreCase(
				userCreateRequest.emailAddress()
		)) {
			throw new ResourceExistsException(
					ApiResponseMessages.ERROR_USER_EMAIL_ALREADY_EXISTS
			);
		}

		User savedUser = User.builder()
				.firstName(userCreateRequest.firstName())
				.lastName(userCreateRequest.lastName())
				.emailAddress(userCreateRequest.emailAddress())
				.userType(userCreateRequest.userType())
				.gender(userCreateRequest.gender())
				.phoneNumber(userCreateRequest.phoneNumber())
				.isDeleted(false)
				.profileColorCode(userCreateRequest.profileColourCode())
				.isActive(false)
				.isVerified(false)
				.verifiedAt(null)
				.lastLoginAt(null)
				.deletedAt(null)
				.passwordChangedAt(null)
				.build();

		if (userCreateRequest.userType() == UserType.ADMIN) {
			String password = generateAdminPassword();

			savedUser.setPassword(password);
			savedUser = userRepository.save(savedUser);

			Map<String, String> otp = userOtpService.generateOtpForUser(
					savedUser.getId(),
					UserOtpType.ACCOUNT_ACTIVATION
			);

			emailSenderService.sendAdminAccountActivationEmail(
					savedUser.getEmailAddress(),
					userCreateRequest.firstName()
							+ " "
							+ userCreateRequest.lastName(),
					password,
					otp.get("otp")
			);

			return buildUserCreateResponseWithOtp(
					savedUser,
					otp.get("otp")
			);

		} else {
			savedUser.setPassword(
					passwordEncoder.encode(userCreateRequest.password())
			);
		}

		savedUser = userRepository.save(savedUser);

		Map<String, String> otp = userOtpService.generateOtpForUser(
				savedUser.getId(),
				UserOtpType.ACCOUNT_ACTIVATION
		);

		return buildUserCreateResponseWithOtp(
				savedUser,
				otp.get("otp")
		);
	}

	public UserCreateResponse adminCreateUser(
			UserCreateRequest userCreateRequest,
			boolean activateAccount
	) {
		if (userRepository.existsByEmailAddressIgnoreCase(
				userCreateRequest.emailAddress()
		)) {
			throw new ResourceExistsException(
					ApiResponseMessages.ERROR_USER_EMAIL_ALREADY_EXISTS
			);
		}

		User savedUser = User.builder()
				.firstName(userCreateRequest.firstName())
				.lastName(userCreateRequest.lastName())
				.emailAddress(userCreateRequest.emailAddress())
				.userType(userCreateRequest.userType())
				.gender(userCreateRequest.gender())
				.isDeleted(false)
				.profileColorCode(userCreateRequest.profileColourCode())
				.isActive(activateAccount)
				.isVerified(activateAccount)
				.verifiedAt(
						activateAccount ? LocalDateTime.now() : null
				)
				.lastLoginAt(null)
				.deletedAt(null)
				.passwordChangedAt(null)
				.build();

		if (userCreateRequest.userType() == UserType.ADMIN) {
			String password = generateAdminPassword();

			savedUser.setPassword(password);
			savedUser = userRepository.save(savedUser);

		} else {
			savedUser.setPassword(
					passwordEncoder.encode(userCreateRequest.password())
			);

			savedUser = userRepository.save(savedUser);
		}

		return buildUserCreateResponse(savedUser);
	}

	private String generateAdminPassword() {
		String letters =
				"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

		String digits = "0123456789";
		String all = letters + digits;

		SecureRandom random = new SecureRandom();
		StringBuilder password = new StringBuilder();

		// Ensure at least one letter.
		password.append(
				letters.charAt(random.nextInt(letters.length()))
		);

		// Ensure at least one digit.
		password.append(
				digits.charAt(random.nextInt(digits.length()))
		);

		// Fill remaining characters.
		for (int i = 2; i < 8; i++) {
			password.append(
					all.charAt(random.nextInt(all.length()))
			);
		}

		// Shuffle to avoid predictable positions.
		char[] chars = password.toString().toCharArray();

		for (int i = chars.length - 1; i > 0; i--) {
			int j = random.nextInt(i + 1);

			char temp = chars[i];
			chars[i] = chars[j];
			chars[j] = temp;
		}

		return new String(chars);
	}

	private UserCreateResponse buildUserCreateResponse(User user) {
		return UserCreateResponse.builder()
				.userId(user.getId())
				.firstName(user.getFirstName())
				.lastName(user.getLastName())
				.emailAddress(user.getEmailAddress())
				.phoneNumber(user.getPhoneNumber())
				.userType(user.getUserType())
				.profilePictureUrl(user.getProfilePictureUrl())
				.dateOfBirth(user.getDateOfBirth())
				.isActive(user.isActive())
				.gender(user.getGender())
				.createdAt(user.getCreatedAt())
				.updatedAt(user.getUpdatedAt())
				.build();
	}

	private UserCreateResponse buildUserCreateResponseWithOtp(
			User user,
			String otp
	) {
		return UserCreateResponse.builder()
				.userId(user.getId())
				.firstName(user.getFirstName())
				.lastName(user.getLastName())
				.emailAddress(user.getEmailAddress())
				.phoneNumber(user.getPhoneNumber())
				.userType(user.getUserType())
				.profilePictureUrl(user.getProfilePictureUrl())
				.dateOfBirth(user.getDateOfBirth())
				.isActive(user.isActive())
				.createdAt(user.getCreatedAt())
				.updatedAt(user.getUpdatedAt())
				.otp(otp)
				.gender(user.getGender())
				.isVerified(user.isVerified())
				.age(
						user.getDateOfBirth() != null
								? calculateAge(user.getDateOfBirth())
								: ""
				)
				.build();
	}

	public UserResponse getUserById(String userId) {
		return buildUserResponse(
				userRepository.findById(userId)
						.orElseThrow(() ->
								new ResourceNotFoundException(
										ApiResponseMessages.ERROR_USER_NOT_FOUND
								)
						)
		);
	}

	private UserResponse buildUserResponse(User user) {
		return UserResponse.builder()
				.userId(user.getId())
				.firstName(user.getFirstName())
				.lastName(user.getLastName())
				.emailAddress(user.getEmailAddress())
				.phoneNumber(user.getPhoneNumber())
				.userType(user.getUserType())
				.profilePictureUrl(user.getProfilePictureUrl())
				.dateOfBirth(user.getDateOfBirth())
				.isActive(user.isActive())
				.createdAt(user.getCreatedAt())
				.updatedAt(user.getUpdatedAt())
				.isVerified(user.isVerified())
				.age(
						user.getDateOfBirth() != null
								? calculateAge(user.getDateOfBirth())
								: ""
				)
				.gender(user.getGender())
				.build();
	}

	public UserResponse toggleUserActiveStatus(
			String userId,
			boolean activeStatus
	) {
		User user = userRepository.findById(userId)
				.orElseThrow(() ->
						new ResourceNotFoundException(
								ApiResponseMessages.ERROR_USER_NOT_FOUND
						)
				);

		if (user.isActive() == activeStatus) {
			throw new InvalidOperationException(
					ApiResponseMessages.ERROR_ACCOUNT_ALREADY_IN_DESIRED_STATE
			);
		}

		user.setActive(activeStatus);

		return buildUserResponse(updateUser(user));
	}

	public UserResponse authenticateUser(
			UserAuthRequest userAuthRequest,
			UserType userType
	) {
		User user = userRepository
				.findByEmailAddressIgnoreCaseAndUserTypeAndIsDeleted(
						userAuthRequest.emailAddress(),
						userType,
						false
				)
				.orElseThrow(() ->
						new InvalidInputException(
								ApiResponseMessages.ERROR_USER_INVALID_EMAIL_OR_PASSWORD
						)
				);

		if (!bCryptPasswordEncoder.matches(
				userAuthRequest.password(),
				user.getPassword()
		)) {
			throw new InvalidInputException(
					ApiResponseMessages.ERROR_USER_INVALID_EMAIL_OR_PASSWORD
			);
		}

		if (!user.isActive()) {
			throw new InvalidOperationException(
					ApiResponseMessages.ERROR_USER_ACCOUNT_NOT_ACTIVE
			);
		}

		if (!user.isVerified()) {
			throw new InvalidOperationException(
					ApiResponseMessages.ERROR_USER_ACCOUNT_NOT_VERIFIED
			);
		}

		return buildUserResponse(user);
	}

	public void resetPassword(
			UserResetPasswordRequest userResetPasswordRequest
	) {
		User user = userRepository
				.findByEmailAddressIgnoreCaseAndIsDeleted(
						userResetPasswordRequest.emailAddress(),
						false
				)
				.orElseThrow(() ->
						new ResourceNotFoundException(
								ApiResponseMessages.ERROR_USER_INVALID_ACCOUNT
						)
				);

		if (!user.isActive()) {
			throw new InvalidOperationException(
					ApiResponseMessages.ERROR_USER_ACCOUNT_NOT_ACTIVE
			);
		}

		if (passwordEncoder.matches(
				userResetPasswordRequest.newPassword(),
				user.getPassword()
		)) {
			throw new InvalidPasswordException(
					ApiResponseMessages.ERROR_USER_SAME_PASSWORD
			);
		}

		userOtpService.validateOtp(
				userResetPasswordRequest.otp(),
				user.getId(),
				UserOtpType.PASSWORD_RESET,
				true
		);

		user.setPassword(
				passwordEncoder.encode(
						userResetPasswordRequest.newPassword()
				)
		);

		user.setPasswordChangedAt(LocalDateTime.now());
		updateUser(user);
	}

	public UserForgotPasswordResponse initiateResetPassword(
			String emailAddress,
			UserType userType
	) {
		User user = userRepository
				.findByEmailAddressIgnoreCaseAndUserTypeAndIsDeleted(
						emailAddress,
						userType,
						false
				)
				.orElseThrow(() ->
						new ResourceNotFoundException(
								ApiResponseMessages.ERROR_USER_INVALID_ACCOUNT
						)
				);

		if (!user.isActive()) {
			throw new InvalidOperationException(
					ApiResponseMessages.ERROR_USER_ACCOUNT_NOT_ACTIVE
			);
		}

		Map<String, String> otp = userOtpService.generateOtpForUser(
				user.getId(),
				UserOtpType.PASSWORD_RESET
		);

		if (user.getUserType() == UserType.ADMIN) {
			emailSenderService.sendAdminPasswordResetEmail(
					user.getEmailAddress(),
					user.getId(),
					otp.get("otp"),
					user.getFirstName() + " " + user.getLastName()
			);
		}

		return UserForgotPasswordResponse.builder()
				.userId(user.getId())
				.build();
	}

	public boolean existsByEmailAddressIgnoreCase(
			String emailAddress
	) {
		return userRepository
				.existsByEmailAddressIgnoreCase(emailAddress);
	}

	public void changeUserPassword(
			String userId,
			UserType userType,
			PasswordChangeRequest passwordChangeRequest
	) {
		User user = userRepository.findById(userId)
				.orElseThrow(() ->
						new ResourceNotFoundException(
								ApiResponseMessages.ERROR_USER_NOT_FOUND
						)
				);

		if (!user.isActive()) {
			throw new InvalidOperationException(
					ApiResponseMessages.ERROR_USER_ACCOUNT_NOT_ACTIVE
			);
		}

		if (!bCryptPasswordEncoder.matches(
				passwordChangeRequest.oldPassword(),
				user.getPassword()
		)) {
			throw new InvalidPasswordException(
					ApiResponseMessages.ERROR_USER_INCORRECT_OLD_PASSWORD
			);
		}

		if (bCryptPasswordEncoder.matches(
				passwordChangeRequest.newPassword(),
				user.getPassword()
		)) {
			throw new InvalidPasswordException(
					ApiResponseMessages.ERROR_USER_SAME_PASSWORD
			);
		}

		user.setPassword(
				bCryptPasswordEncoder.encode(
						passwordChangeRequest.newPassword()
				)
		);

		user.setPasswordChangedAt(LocalDateTime.now());
		updateUser(user);
	}

	public User verifyOtp(
			UserVerifyOtpRequest userVerifyOtpRequest
	) {
		User user = userRepository
				.findByEmailAddressIgnoreCaseAndIsDeleted(
						userVerifyOtpRequest.userEmail(),
						false
				)
				.orElseThrow(() ->
						new ResourceNotFoundException(
								ApiResponseMessages.ERROR_USER_INVALID_ACCOUNT
						)
				);

		userOtpService.validateOtp(
				userVerifyOtpRequest.otp(),
				user.getId(),
				userVerifyOtpRequest.userOtpType(),
				true
		);

		if (user.getUserType() == UserType.ADMIN
				&& userVerifyOtpRequest.userOtpType()
				== UserOtpType.ACCOUNT_ACTIVATION) {

			user.setPassword(
					passwordEncoder.encode(user.getPassword())
			);

			user.setPasswordChangedAt(LocalDateTime.now());
		}

		user.setVerifiedAt(LocalDateTime.now());
		user.setActive(true);
		user.setVerified(true);

		return updateUser(user);
	}

	public void resendOtp(
			String emailAddress,
			UserOtpType userOtpType
	) {
		User user = userRepository
				.findByEmailAddressIgnoreCaseAndIsDeleted(
						emailAddress,
						false
				)
				.orElseThrow(() ->
						new ResourceNotFoundException(
								ApiResponseMessages.ERROR_USER_INVALID_ACCOUNT
						)
				);

		Map<String, String> otp = userOtpService.generateOtpForUser(
				user.getId(),
				userOtpType
		);

		if (user.getUserType() == UserType.ADMIN) {
			emailSenderService.sendAdminAccountActivationEmail(
					user.getEmailAddress(),
					user.getFirstName() + " " + user.getLastName(),
					user.getPassword(),
					otp.get("otp")
			);
		}
	}

	public List<UserResponse> getAllUsersByType(
			UserType userType
	) {
		return userRepository
				.findAllByUserTypeAndIsDeleted(userType, false)
				.stream()
				.map(this::buildUserResponse)
				.toList();
	}

	public List<UserResponse> getAllUsersByIds(
			List<String> userIds
	) {
		return userRepository.findAllById(userIds)
				.stream()
				.map(this::buildUserResponse)
				.toList();
	}

	public void updateUserProfile(
			String userId,
			UserUpdateRequest userUpdateRequest
	) {
		User user = userRepository.findById(userId)
				.orElseThrow(() ->
						new ResourceNotFoundException(
								ApiResponseMessages.ERROR_USER_INVALID_ACCOUNT
						)
				);

		if (!user.isActive() || !user.isVerified()) {
			throw new InvalidOperationException(
					ApiResponseMessages.ERROR_USER_INVALID_ACCOUNT
			);
		}

		if (user.isDeleted()) {
			throw new InvalidOperationException(
					ApiResponseMessages.ERROR_USER_NOT_FOUND
			);
		}

		user.setFirstName(userUpdateRequest.firstName());
		user.setLastName(userUpdateRequest.lastName());
		user.setPhoneNumber(userUpdateRequest.phoneNumber());
		user.setDateOfBirth(userUpdateRequest.dateOfBirth());
		user.setProfilePictureUrl(userUpdateRequest.profilePictureUrl());

		user.setAge(
				userUpdateRequest.dateOfBirth() != null
						? calculateAge(userUpdateRequest.dateOfBirth())
						: ""
		);

		user.setGender(userUpdateRequest.gender());

		updateUser(user);
	}

	public void deleteUserById(String userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() ->
						new ResourceNotFoundException(
								ApiResponseMessages.ERROR_USER_INVALID_ACCOUNT
						)
				);

		userRepository.delete(user);
	}

	public Set<User> getUsersByIds(Set<String> userIds) {
		return new HashSet<>(
				userRepository.findAllById(userIds)
		);
	}

	public void sendAccountSetupCompletionEmail(String userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() ->
						new ResourceNotFoundException(
								ApiResponseMessages.ERROR_USER_INVALID_ACCOUNT
						)
				);

		emailSenderService.sendAccountSetupCompletionEmail(
				user.getEmailAddress(),
				user.getFirstName() + " " + user.getLastName()
		);
	}

	public User getUserByEmailAddress(String emailAddress) {
		return userRepository
				.findByEmailAddressIgnoreCaseAndIsDeleted(
						emailAddress,
						false
				)
				.orElseThrow(() ->
						new ResourceNotFoundException(
								ApiResponseMessages.ERROR_USER_NOT_FOUND
						)
				);
	}

	public Optional<User> findByEmail(String email) {
		return userRepository
				.findByEmailAddressIgnoreCaseAndIsDeleted(
						email,
						false
				);
	}

	public User findById(String userId) {
		return userRepository.findById(userId)
				.orElseThrow(() ->
						new ResourceNotFoundException(
								ApiResponseMessages.ERROR_USER_NOT_FOUND,
								true,
								false
						)
				);
	}

	public UserCreateResponse seedSuperAdmin(
			UserCreateRequest userCreateRequest
	) {
		log.info("Creating configured super admin account");

		User user = User.builder()
				.firstName(userCreateRequest.firstName())
				.lastName(userCreateRequest.lastName())
				.emailAddress(userCreateRequest.emailAddress())
				.userType(UserType.ADMIN)
				.password(
						passwordEncoder.encode(
								userCreateRequest.password()
						)
				)
				.isActive(true)
				.isVerified(true)
				.isDeleted(false)
				.profileColorCode("#000000")
				.build();

		return buildUserCreateResponse(
				userRepository.save(user)
		);
	}

	public String calculateAge(LocalDate dateOfBirth) {
		try {
			return Period.between(
					dateOfBirth,
					LocalDate.now()
			).getYears() + " years";

		} catch (Exception e) {
			return "";
		}
	}

	public Set<String> getAdminEmails() {
		return userRepository
				.findEmailAddressesByUserTypeAndIsDeleted(
						UserType.ADMIN,
						false
				);
	}

	public void updateUserProfilePicture(
			String userId,
			String profilePictureUrl
	) {
		User user = userRepository.findById(userId)
				.orElseThrow(() ->
						new ResourceNotFoundException(
								ApiResponseMessages.ERROR_USER_INVALID_ACCOUNT
						)
				);

		user.setProfilePictureUrl(profilePictureUrl);
		updateUser(user);
	}

	public Optional<User> findByEmailAndUserType(
			String email,
			UserType userType
	) {
		return userRepository
				.findByEmailAddressIgnoreCaseAndUserTypeAndIsDeleted(
						email,
						userType,
						false
				);
	}

	public User createAdminUserFromAdminModule(
			String firstName,
			String lastName,
			String emailAddress,
			String password,
			String phoneNumber,
			String profilePictureUrl,
			String profileColourCode,
			Gender gender,
			boolean activateAccount
	) {
		if (userRepository.existsByEmailAddressIgnoreCase(
				emailAddress
		)) {
			throw new ResourceExistsException(
					ApiResponseMessages.ERROR_USER_EMAIL_ALREADY_EXISTS
			);
		}

		User user = User.builder()
				.firstName(
						firstName == null ? null : firstName.trim()
				)
				.lastName(
						lastName == null ? null : lastName.trim()
				)
				.emailAddress(
						emailAddress == null
								? null
								: emailAddress.trim().toLowerCase()
				)
				.phoneNumber(
						phoneNumber == null
								? null
								: phoneNumber.trim()
				)
				.password(passwordEncoder.encode(password))
				.userType(UserType.ADMIN)
				.profilePictureUrl(profilePictureUrl)
				.profileColorCode(profileColourCode)
				.gender(gender)
				.isDeleted(false)
				.isActive(activateAccount)
				.isVerified(activateAccount)
				.verifiedAt(
						activateAccount ? LocalDateTime.now() : null
				)
				.lastLoginAt(null)
				.deletedAt(null)
				.passwordChangedAt(LocalDateTime.now())
				.build();

		return userRepository.save(user);
	}

	public User authenticateAdminUserFromAdminModule(
			String emailAddress,
			String password
	) {
		User user = userRepository
				.findByEmailAddressIgnoreCaseAndUserTypeAndIsDeleted(
						emailAddress == null
								? null
								: emailAddress.trim().toLowerCase(),
						UserType.ADMIN,
						false
				)
				.orElseThrow(() ->
						new InvalidInputException(
								ApiResponseMessages.ERROR_USER_INVALID_EMAIL_OR_PASSWORD
						)
				);

		if (!bCryptPasswordEncoder.matches(
				password,
				user.getPassword()
		)) {
			throw new InvalidInputException(
					ApiResponseMessages.ERROR_USER_INVALID_EMAIL_OR_PASSWORD
			);
		}

		if (!user.isActive()) {
			throw new InvalidOperationException(
					ApiResponseMessages.ERROR_USER_ACCOUNT_NOT_ACTIVE
			);
		}

		if (!user.isVerified()) {
			throw new InvalidOperationException(
					ApiResponseMessages.ERROR_USER_ACCOUNT_NOT_VERIFIED
			);
		}

		user.setLastLoginAt(LocalDateTime.now());

		return updateUser(user);
	}

	public User updateAdminUserFromAdminModule(
			String userId,
			AdminUpdateRequest request
	) {
		User user = userRepository.findById(userId)
				.orElseThrow(() ->
						new ResourceNotFoundException(
								ApiResponseMessages.ERROR_USER_NOT_FOUND
						)
				);

		if (request.firstName() != null) {
			user.setFirstName(request.firstName().trim());
		}

		if (request.lastName() != null) {
			user.setLastName(request.lastName().trim());
		}

		if (request.phoneNumber() != null) {
			user.setPhoneNumber(request.phoneNumber().trim());
		}

		if (request.profilePictureUrl() != null) {
			user.setProfilePictureUrl(
					request.profilePictureUrl().trim()
			);
		}

		if (request.dateOfBirth() != null) {
			user.setDateOfBirth(request.dateOfBirth());
			user.setAge(calculateAge(request.dateOfBirth()));
		}

		if (request.gender() != null) {
			user.setGender(request.gender());
		}

		return updateUser(user);
	}

	public User getAdminUserByIdFromAdminModule(String userId) {
		User user = userRepository.findById(userId)
				.orElseThrow(() ->
						new ResourceNotFoundException(
								ApiResponseMessages.ERROR_USER_NOT_FOUND
						)
				);

		if (user.getUserType() != UserType.ADMIN
				|| user.isDeleted()) {

			throw new ResourceNotFoundException(
					ApiResponseMessages.ERROR_USER_NOT_FOUND
			);
		}

		return user;
	}

	public User getAdminUserByEmailFromAdminModule(
			String emailAddress
	) {
		return userRepository
				.findByEmailAddressIgnoreCaseAndUserTypeAndIsDeleted(
						emailAddress == null
								? null
								: emailAddress.trim().toLowerCase(),
						UserType.ADMIN,
						false
				)
				.orElseThrow(() ->
						new ResourceNotFoundException(
								ApiResponseMessages.ERROR_USER_NOT_FOUND
						)
				);
	}

	public User toggleAdminUserActiveStatusFromAdminModule(
			String userId,
			boolean active
	) {
		User user = getAdminUserByIdFromAdminModule(userId);
		user.setActive(active);

		return updateUser(user);
	}

	public void softDeleteAdminUserFromAdminModule(
			String userId
	) {
		User user = getAdminUserByIdFromAdminModule(userId);

		user.setDeleted(true);
		user.setActive(false);
		user.setDeletedAt(LocalDateTime.now());

		updateUser(user);
	}

	public Map<String, String> generatePasswordResetOtpForAdmin(
			String userId
	) {
		User user = getAdminUserByIdFromAdminModule(userId);

		if (!user.isActive()) {
			throw new InvalidOperationException(
					ApiResponseMessages.ERROR_USER_ACCOUNT_NOT_ACTIVE
			);
		}

		if (!user.isVerified()) {
			throw new InvalidOperationException(
					ApiResponseMessages.ERROR_USER_ACCOUNT_NOT_VERIFIED
			);
		}

		Map<String, String> otp = userOtpService.generateOtpForUser(
				user.getId(),
				UserOtpType.PASSWORD_RESET
		);

		emailSenderService.sendAdminPasswordResetEmail(
				user.getEmailAddress(),
				user.getId(),
				otp.get("otp"),
				user.getFirstName() + " " + user.getLastName()
		);

		return otp;
	}

	public void resetAdminPasswordFromAdminModule(
			String userId,
			String otp,
			String newPassword
	) {
		User user = getAdminUserByIdFromAdminModule(userId);

		if (!user.isActive()) {
			throw new InvalidOperationException(
					ApiResponseMessages.ERROR_USER_ACCOUNT_NOT_ACTIVE
			);
		}

		if (!user.isVerified()) {
			throw new InvalidOperationException(
					ApiResponseMessages.ERROR_USER_ACCOUNT_NOT_VERIFIED
			);
		}

		if (passwordEncoder.matches(
				newPassword,
				user.getPassword()
		)) {
			throw new InvalidPasswordException(
					ApiResponseMessages.ERROR_USER_SAME_PASSWORD
			);
		}

		userOtpService.validateOtp(
				otp,
				user.getId(),
				UserOtpType.PASSWORD_RESET,
				true
		);

		user.setPassword(
				passwordEncoder.encode(newPassword)
		);

		user.setPasswordChangedAt(LocalDateTime.now());

		updateUser(user);
	}

	public void save(User updatedUser) {
		userRepository.save(updatedUser);
	}

	public List<User> findUsers(Query query) {
		if (query == null) {
			throw new InvalidInputException(
					"User query is required"
			);
		}

		return mongoTemplate.find(query, User.class);
	}
}