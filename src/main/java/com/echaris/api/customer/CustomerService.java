package com.echaris.api.customer;

import com.echaris.api.admin.AdminRepository;
import com.echaris.api.notification.NotificationService;
import com.echaris.api.notification.NotificationResponse;
import com.echaris.api.customer.request.*;
import com.echaris.api.customer.response.CustomerAuthResponse;
import com.echaris.api.customer.response.CustomerForgotPasswordResponse;
import com.echaris.api.customer.response.CustomerKycResponse;
import com.echaris.api.customer.response.CustomerResponse;
import com.echaris.api.userauthmgt.user.User;
import com.echaris.api.userauthmgt.user.UserService;
import com.echaris.api.userauthmgt.user.UserType;
import com.echaris.api.userauthmgt.user.request.PasswordChangeRequest;
import com.echaris.api.userauthmgt.user.request.UserAuthRequest;
import com.echaris.api.userauthmgt.user.request.UserCreateRequest;
import com.echaris.api.userauthmgt.user.request.UserResetPasswordRequest;
import com.echaris.api.userauthmgt.user.request.UserVerifyOtpRequest;
import com.echaris.api.userauthmgt.user.response.UserCreateResponse;
import com.echaris.api.userauthmgt.user.response.UserResponse;
import com.echaris.api.userauthmgt.userotp.UserOtpService;
import com.echaris.api.userauthmgt.userotp.UserOtpType;
import com.echaris.api.utils.PaginatedData;
import com.echaris.api.utils.UtilsService;
import com.echaris.api.utils.appsecurity.JwtService;
import com.echaris.api.utils.emailsenderservice.EmailSenderService;
import com.echaris.api.utils.exception.*;
import com.echaris.api.utils.fileupload.FileUploadService;
import com.echaris.api.utils.fileupload.ManagedFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.security.InvalidParameterException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserService userService;
    private final UserOtpService userOtpService;
    private final JwtService jwtService;
    private final EmailSenderService emailSenderService;
    private final FileUploadService fileUploadService;
    private final UtilsService utilsService;
    private final MongoTemplate mongoTemplate;
    private final NotificationService notificationService;
    private final AdminRepository adminRepository;
    private final jakarta.servlet.http.HttpServletRequest httpServletRequest;

    @Transactional
    public void register(CustomerCreateRequest request) {
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        if (!request.acceptedConsent()) {
            throw new InvalidParameterException("Content Not Accepted");
        }

        log.info(
                "Attempting to register new customer with email: {}",
                request.emailAddress()
        );

        validatePassword(request.password());

        UserCreateResponse createdUser = userService.createUser(
                UserCreateRequest.builder()
                        .firstName(request.firstName())
                        .lastName(request.lastName())
                        .emailAddress(
                                normalizeEmail(request.emailAddress())
                        )
                        .password(request.password())
                        .userType(UserType.CUSTOMER)
                        .phoneNumber(request.phoneNumber())
                        .build()
        );

        User savedUser = userService.findById(createdUser.userId());

        Customer customer = Customer.builder()
                .userId(savedUser.getId())
                .customerNumber(generateCustomerNumber())
                .active(false)
                .deleted(false)
                .lastLoginAt(null)
                .deletedAt(null)
                .consentAcceptedAt(LocalDateTime.now())
                .acceptedConsent(request.acceptedConsent())
                .build();

        customerRepository.save(customer);

        emailSenderService.sendAccountActivationEmail(
                savedUser.getEmailAddress(),
                savedUser.getId(),
                buildFullName(savedUser),
                requireText(
                        createdUser.otp(),
                        "Activation OTP could not be generated"
                )
        );

        log.info(
                "Customer registration completed; email verification is required for email: {}",
                savedUser.getEmailAddress()
        );
    }

    @Transactional
    public CustomerResponse verifyAccount(
            CustomerVerifyOtpRequest request
    ) {
        log.info(
                "Attempting to verify customer account for email: {}",
                request != null ? request.emailAddress() : "null"
        );

        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        User user = findCustomerUserByEmail(request.emailAddress());
        Customer customer = findCustomerByUserId(user.getId());

        if (user.isVerified()) {
            log.info(
                    "Customer verification failed: Account already verified for email: {}",
                    user.getEmailAddress()
            );

            throw new InvalidOperationException(
                    "Customer account is already verified"
            );
        }

        User savedUser = userService.verifyOtp(
                new UserVerifyOtpRequest(
                        request.otp(),
                        user.getEmailAddress(),
                        UserOtpType.ACCOUNT_ACTIVATION
                )
        );

        customer.setActive(true);
        customer.setUpdatedAt(LocalDateTime.now());

        Customer savedCustomer = customerRepository.save(customer);

        userService.sendAccountSetupCompletionEmail(savedUser.getId());

        log.info(
                "Customer account verified successfully for email: {}",
                savedUser.getEmailAddress()
        );

        return CustomerResponse.from(savedCustomer, savedUser);
    }

    public void resendVerificationOtp(
            CustomerResendOtpRequest request
    ) {
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        log.info(
                "Attempting to resend OTP for email: {}, type: {}",
                request.emailAddress(),
                request.userOtpType()
        );

        User user = findCustomerUserByEmail(request.emailAddress());

        if (request.userOtpType() == UserOtpType.ACCOUNT_ACTIVATION) {
            if (user.isVerified()) {
                log.info(
                        "Resend OTP failed: Account already verified for email: {}",
                        user.getEmailAddress()
                );

                throw new InvalidOperationException(
                        "Customer account is already verified"
                );
            }

            Map<String, String> otp = userOtpService.generateOtpForUser(
                    user.getId(),
                    UserOtpType.ACCOUNT_ACTIVATION
            );

            emailSenderService.sendAccountActivationEmail(
                    user.getEmailAddress(),
                    user.getId(),
                    buildFullName(user),
                    otp.get("otp")
            );

        } else if (request.userOtpType() == UserOtpType.PASSWORD_RESET) {
            Map<String, String> otp = userOtpService.generateOtpForUser(
                    user.getId(),
                    UserOtpType.PASSWORD_RESET
            );

            emailSenderService.sendPasswordResetEmail(
                    user.getEmailAddress(),
                    user.getId(),
                    otp.get("otp"),
                    buildFullName(user)
            );

        } else {
            log.info(
                    "Resend OTP failed: Invalid OTP type requested: {}",
                    request.userOtpType()
            );

            throw new InvalidInputException(
                    "Use ACCOUNT_ACTIVATION or PASSWORD_RESET"
            );
        }

        log.info(
                "Customer OTP email submitted successfully: purpose={}",
                request.userOtpType()
        );
    }

    @Transactional
    public CustomerAuthResponse login(CustomerLoginRequest request) {
        log.info(
                "Attempting customer login for email: {}",
                request != null ? request.emailAddress() : "null"
        );

        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        UserResponse authenticatedUser;

        try {
            authenticatedUser = userService.authenticateUser(
                    new UserAuthRequest(
                            normalizeEmail(request.emailAddress()),
                            request.password(),
                            null
                    ),
                    UserType.CUSTOMER
            );

        } catch (InvalidInputException exception) {
            findCustomerUserByEmail(request.emailAddress());

            log.info(
                    "Login failed: Invalid credentials for email: {}",
                    request.emailAddress()
            );

            throw new UnauthorizedException(
                    "Invalid email address or password"
            );

        } catch (InvalidOperationException exception) {
            User account = findCustomerUserByEmail(request.emailAddress());

            if (!account.isVerified()) {
                log.info(
                        "Login failed: Account not verified for email: {}",
                        request.emailAddress()
                );

                throw new InvalidOperationException(
                        "Customer account has not been verified"
                );
            }

            if (!account.isActive()) {
                log.info(
                        "Login failed: Account inactive for email: {}",
                        request.emailAddress()
                );

                throw new UnauthorizedException(
                        "Customer account is inactive"
                );
            }

            log.info(
                    "Login failed: Authentication operation failed for email: {}",
                    request.emailAddress()
            );

            throw exception;
        }

        User user = findCustomerUserById(authenticatedUser.userId());
        Customer customer = findCustomerByUserId(user.getId());

        if (!customer.isActive()) {
            log.info(
                    "Login failed: Customer profile inactive for user ID: {}",
                    user.getId()
            );

            throw new UnauthorizedException("Customer account is inactive");
        }

        LocalDateTime loginTime = LocalDateTime.now();

        user.setLastLoginAt(loginTime);
        customer.setLastLoginAt(loginTime);
        customer.setUpdatedAt(loginTime);

        User savedUser = userService.updateUser(user);
        Customer savedCustomer = customerRepository.save(customer);

        Map<String, Object> claims =
                buildJwtClaims(savedCustomer, savedUser);

        String accessToken = jwtService.generateToken(
                savedUser.getEmailAddress(),
                claims
        );

        String refreshToken = jwtService.generateRefreshToken(
                savedUser.getEmailAddress(),
                claims
        );

        if (accessToken == null
                || accessToken.isBlank()
                || refreshToken == null
                || refreshToken.isBlank()) {

            log.info(
                    "Login failed: Could not generate tokens for email: {}",
                    savedUser.getEmailAddress()
            );

            throw new ErrorProcessingRequestException(
                    "Unable to create the sign-in session"
            );
        }

        log.info(
                "Customer login completed successfully for email: {}",
                savedUser.getEmailAddress()
        );

        return new CustomerAuthResponse(
                accessToken,
                refreshToken,
                CustomerResponse.from(savedCustomer, savedUser)
        );
    }

    public CustomerResponse getCurrentProfile() {
        log.info("Fetching current customer profile");

        User user = findCustomerUserByEmail(getCurrentPrincipalEmail());
        Customer customer = findCustomerByUserId(user.getId());

        if (!customer.isActive()) {
            log.info(
                    "Profile fetch failed: Customer account inactive for user ID: {}",
                    user.getId()
            );

            throw new UnauthorizedException("Customer account is inactive");
        }

        return CustomerResponse.from(customer, user);
    }

    @Transactional
    public CustomerResponse updateCurrentProfile(
            CustomerUpdateRequest request
    ) {
        log.info("Updating current customer profile");

        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        User user = findCustomerUserByEmail(getCurrentPrincipalEmail());
        Customer customer = findCustomerByUserId(user.getId());

        String previousProfilePicture = user.getProfilePictureUrl();

        if (request.firstName() != null) {
            user.setFirstName(
                    requireText(
                            request.firstName(),
                            "First name cannot be blank"
                    )
            );
        }

        if (request.lastName() != null) {
            user.setLastName(
                    requireText(
                            request.lastName(),
                            "Last name cannot be blank"
                    )
            );
        }

        if (request.phoneNumber() != null) {
            user.setPhoneNumber(clean(request.phoneNumber()));
        }

        if (request.dateOfBirth() != null) {
            user.setDateOfBirth(request.dateOfBirth());
            user.setAge(calculateAge(request.dateOfBirth()));
        }

        if (request.gender() != null) {
            user.setGender(request.gender());
        }

        if (request.profilePictureUrl() != null) {
            user.setProfilePictureUrl(clean(request.profilePictureUrl()));
        }

        if (request.marketingEmailsEnabled() != null) {
            customer.setMarketingEmailsEnabled(
                    request.marketingEmailsEnabled()
            );
        }

        user.setUpdatedAt(LocalDateTime.now());
        customer.setUpdatedAt(LocalDateTime.now());

        User savedUser = userService.updateUser(user);
        Customer savedCustomer = customerRepository.save(customer);

        fileUploadService.deleteOldFileIfChanged(
                previousProfilePicture,
                savedUser.getProfilePictureUrl()
        );

        log.info(
                "Customer profile updated successfully for user ID: {}",
                savedUser.getId()
        );

        return CustomerResponse.from(savedCustomer, savedUser);
    }

    @Transactional
    public CustomerKycResponse submitKycDocument(
            CustomerKycRequest request
    ) {
        if (request == null || request.documentType() == null) {
            throw new InvalidInputException(
                    "Request body and document type are required"
            );
        }

        Customer customer = requireCurrentKycCustomer();

        String number = requireText(
                request.documentNumber(),
                "Document number is required"
        ).toUpperCase(Locale.ROOT);

        if (number.length() > 100) {
            throw new InvalidInputException(
                    "Document number must not exceed 100 characters"
            );
        }

        if (request.documentType() == CustomerKycDocumentType.NIN
                && !number.matches("[0-9]{11}")) {
            throw new InvalidInputException(
                    "NIN must contain exactly 11 digits without spaces"
            );
        }

        Set<CustomerKycDocuments> documents = kycDocuments(customer);

        CustomerKycDocuments previous = documents.stream()
                .filter(document ->
                        document.getDocumentType() == request.documentType()
                )
                .findFirst()
                .orElse(null);

        if (previous != null
                && previous.getReviewStatus()
                != CustomerKycReviewStatus.REJECTED) {
            throw new InvalidOperationException(
                    "This document type has already been submitted"
            );
        }

        CustomerKycDocuments document = CustomerKycDocuments.builder()
                .documentType(request.documentType())
                .documentNumber(number)
                .frontViewUrl(
                        validateKycFile(
                                request.frontViewUrl(),
                                "Front document"
                        )
                )
                .backViewUrl(
                        validateKycFile(
                                request.backViewUrl(),
                                "Back document"
                        )
                )
                .submittedAt(LocalDateTime.now())
                .submissionVersion(
                        previous == null
                                ? 1
                                : previous.getSubmissionVersion() + 1
                )
                .build();

        documents.remove(previous);
        documents.add(document);

        Customer saved = saveKyc(customer, documents);

        notifyKycSubmission(saved, document);

        return CustomerKycResponse.from(saved);
    }

    public CustomerKycResponse getCurrentKycDocuments() {
        return CustomerKycResponse.from(requireCurrentKycCustomer());
    }

    public CustomerKycResponse getCustomerKycForAdmin(String customerId) {
        requireKycAdminUserId();

        return CustomerKycResponse.from(findCustomerById(customerId));
    }

    public PaginatedData getKycSubmissions(
            CustomerKycReviewStatus status,
            int page,
            int size
    ) {
        requireKycAdminUserId();

        if (page < 1 || page > 10000 || size < 1 || size > 100) {
            throw new InvalidInputException("Invalid page or size");
        }

        Criteria documentCriteria = switch (status) {
            case APPROVED ->
                    Criteria.where("isApproved").is(true);
            case REJECTED ->
                    Criteria.where("isApproved").ne(true)
                            .and("rejectedAt").ne(null);
            case PENDING ->
                    Criteria.where("isApproved").ne(true)
                            .and("rejectedAt").is(null);
        };

        Criteria criteria = Criteria.where("deleted")
                .is(false)
                .and("kycDocuments")
                .elemMatch(documentCriteria);

        long count = mongoTemplate.count(
                new Query(criteria),
                Customer.class
        );

        Query query = new Query(criteria)
                .with(
                        Sort.by(
                                Sort.Order.desc("updatedAt"),
                                Sort.Order.asc("id")
                        )
                )
                .skip((long) (page - 1) * size)
                .limit(size);

        List<CustomerKycResponse> rows = mongoTemplate
                .find(query, Customer.class)
                .stream()
                .map(CustomerKycResponse::from)
                .toList();

        return utilsService.createPaginatedData(rows, page, size, count);
    }

    @Transactional
    public CustomerKycResponse approveCustomerKyc(
            String customerId,
            CustomerKycDocumentType documentType,
            CustomerKycReviewRequest request
    ) {
        return reviewCustomerKyc(
                customerId,
                documentType,
                request,
                true
        );
    }

    @Transactional
    public CustomerKycResponse rejectCustomerKyc(
            String customerId,
            CustomerKycDocumentType documentType,
            CustomerKycReviewRequest request
    ) {
        return reviewCustomerKyc(
                customerId,
                documentType,
                request,
                false
        );
    }

    private CustomerKycResponse reviewCustomerKyc(
            String customerId,
            CustomerKycDocumentType documentType,
            CustomerKycReviewRequest request,
            boolean approved
    ) {
        String adminUserId = requireKycAdminUserId();

        if (request == null
                || request.submissionVersion() == null
                || request.submissionVersion() < 0
                || documentType == null) {
            throw new InvalidInputException(
                    "Document type and submission version are required"
            );
        }

        String reason = approved
                ? null
                : requireText(
                request.rejectionReason(),
                "A rejection reason is required"
        );

        if (reason != null && reason.length() > 1000) {
            throw new InvalidInputException(
                    "Rejection reason must not exceed 1000 characters"
            );
        }

        Customer customer = findCustomerById(customerId);

        Set<CustomerKycDocuments> documents = kycDocuments(customer);

        CustomerKycDocuments current = documents.stream()
                .filter(document ->
                        document.getDocumentType() == documentType
                )
                .findFirst()
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "KYC document not found"
                        )
                );

        if (current.getSubmissionVersion()
                != request.submissionVersion()) {
            throw new InvalidOperationException(
                    "The customer resubmitted this document. Refresh before reviewing"
            );
        }

        CustomerKycReviewStatus desired = approved
                ? CustomerKycReviewStatus.APPROVED
                : CustomerKycReviewStatus.REJECTED;

        if (current.getReviewStatus() == desired
                && Objects.equals(
                current.getRejectionReason(),
                reason
        )) {
            return CustomerKycResponse.from(customer);
        }

        LocalDateTime now = LocalDateTime.now();

        CustomerKycDocuments reviewed = current.toBuilder()
                .isApproved(approved)
                .approvedAt(approved ? now : null)
                .approvedBy(approved ? adminUserId : null)
                .rejectedAt(approved ? null : now)
                .rejectedBy(approved ? null : adminUserId)
                .rejectionReason(reason)
                .build();

        documents.remove(current);
        documents.add(reviewed);

        Customer saved = saveKyc(customer, documents);

        notifyKycReview(saved, reviewed);

        return CustomerKycResponse.from(saved);
    }

    private Set<CustomerKycDocuments> kycDocuments(Customer customer) {
        return customer.getKycDocuments() == null
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(customer.getKycDocuments());
    }

    private Customer saveKyc(
            Customer customer,
            Set<CustomerKycDocuments> documents
    ) {
        Long revision = customer.getKycRevision();

        Query query = Query.query(
                Criteria.where("id")
                        .is(customer.getId())
                        .and("deleted")
                        .is(false)
                        .and("kycRevision")
                        .is(revision)
        );

        Update update = new Update()
                .set("kycDocuments", documents)
                .set("doneKyc", !documents.isEmpty())
                .set(
                        "kycAccepted",
                        documents.stream()
                                .anyMatch(CustomerKycDocuments::isApproved)
                )
                .set(
                        "kycRevision",
                        revision == null ? 1L : revision + 1L
                )
                .set("updatedAt", LocalDateTime.now());

        Customer saved = mongoTemplate.findAndModify(
                query,
                update,
                FindAndModifyOptions.options().returnNew(true),
                Customer.class
        );

        if (saved == null) {
            throw new InvalidOperationException(
                    "KYC changed during this request. Refresh and try again"
            );
        }

        return saved;
    }

    private String requireKycAdminUserId() {
        Object value = httpServletRequest.getAttribute("validatedClaims");

        if (!(value instanceof io.jsonwebtoken.Claims claims)
                || !"ADMIN".equals(
                claims.get("userType", String.class)
        )) {
            throw new UnauthorizedException(
                    "Admin authentication is required"
            );
        }

        String userId = claims.get("userId", String.class);

        if (userId == null || userId.isBlank()) {
            throw new UnauthorizedException(
                    "Admin identity is unavailable"
            );
        }

        User admin = userService.findById(userId);

        if (admin.getUserType() != UserType.ADMIN
                || admin.isDeleted()
                || !admin.isActive()
                || !admin.isVerified()) {
            throw new UnauthorizedException(
                    "Admin account is unavailable"
            );
        }

        return admin.getId();
    }

    private void notifyKycSubmission(
            Customer customer,
            CustomerKycDocuments document
    ) {
        User owner = findCustomerUserById(customer.getUserId());

        sendKycMessages(
                owner,
                customer,
                document,
                "KYC_SUBMITTED",
                "KYC submission received",
                "Your identity document has been submitted for review."
        );

        Set<String> adminUserIds = adminRepository
                .findAllByDeleted(false)
                .stream()
                .filter(admin -> admin.isActive())
                .map(admin -> admin.getUserId())
                .collect(Collectors.toSet());

        // Fetch admin users in one batch.
        if (!adminUserIds.isEmpty()) {
            for (User admin : userService.getUsersByIds(adminUserIds)) {
                if (admin.getUserType() == UserType.ADMIN
                        && !admin.isDeleted()
                        && admin.isActive()
                        && admin.isVerified()) {

                    sendKycMessages(
                            admin,
                            customer,
                            document,
                            "KYC_SUBMITTED",
                            "New KYC submission",
                            "A customer submitted an identity document for review."
                    );
                }
            }
        }
    }

    private void notifyKycReview(
            Customer customer,
            CustomerKycDocuments document
    ) {
        boolean approved = document.isApproved();

        sendKycMessages(
                findCustomerUserById(customer.getUserId()),
                customer,
                document,
                approved ? "KYC_APPROVED" : "KYC_REJECTED",
                approved
                        ? "KYC document approved"
                        : "KYC document rejected",
                approved
                        ? "Your identity document has been approved."
                        : "Your identity document was rejected. Please correct it and resubmit."
        );
    }

    private void sendKycMessages(
            User recipient,
            Customer customer,
            CustomerKycDocuments document,
            String type,
            String title,
            String message
    ) {
        Map<String, String> data = new LinkedHashMap<>();

        data.put("documentType", document.getDocumentType().name());
        data.put(
                "submissionVersion",
                Long.toString(document.getSubmissionVersion())
        );

        if (StringUtils.hasText(document.getRejectionReason())) {
            data.put("reason", document.getRejectionReason());
        }

        notificationService.create(
                recipient.getId(),
                type,
                title,
                message,
                "CUSTOMER_KYC",
                customer.getId(),
                data
        );

        Map<String, String> variables = new LinkedHashMap<>();

        variables.put("title", title);
        variables.put("name", safeText(recipient.getFirstName()));
        variables.put("message", message);
        variables.put(
                "documentType",
                document.getDocumentType().name()
        );
        variables.put("customerId", customer.getId());
        variables.put(
                "reason",
                safeText(document.getRejectionReason())
        );

        emailSenderService.sendTemplateEmailAfterCommit(
                recipient.getEmailAddress(),
                title,
                "kyc-notification",
                variables
        );
    }

    public List<NotificationResponse> getMyKycNotifications(
            int page,
            int size
    ) {
        return notificationService.getNotifications(
                requireCurrentKycCustomer().getUserId(),
                page,
                size
        );
    }

    public NotificationResponse readMyKycNotification(
            String notificationId
    ) {
        return notificationService.markRead(
                requireCurrentKycCustomer().getUserId(),
                notificationId
        );
    }

    public List<NotificationResponse> getAdminKycNotifications(
            int page,
            int size
    ) {
        return notificationService.getNotifications(
                requireKycAdminUserId(),
                page,
                size
        );
    }

    public NotificationResponse readAdminKycNotification(
            String notificationId
    ) {
        return notificationService.markRead(
                requireKycAdminUserId(),
                notificationId
        );
    }

    private Customer requireCurrentKycCustomer() {
        User user = findCustomerUserByEmail(getCurrentPrincipalEmail());

        if (user.isDeleted() || !user.isActive() || !user.isVerified()) {
            throw new UnauthorizedException(
                    "An active, verified customer account is required"
            );
        }

        Customer customer = findCustomerByUserId(user.getId());

        if (!customer.isActive()) {
            throw new UnauthorizedException(
                    "Customer account is inactive"
            );
        }

        return customer;
    }

    private String validateKycFile(String value, String label) {
        String url = requireText(value, label + " URL is required");

        if (url.length() > 2048) {
            throw new InvalidInputException(label + " URL is too long");
        }

        URI uri;

        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException exception) {
            throw new InvalidInputException(label + " URL is invalid");
        }

        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"res.cloudinary.com".equalsIgnoreCase(uri.getHost())
                || uri.getUserInfo() != null) {
            throw new InvalidInputException(
                    label + " must use a secure Cloudinary URL"
            );
        }

        // The existing upload endpoint must have recorded the URL.
        ManagedFile file = fileUploadService.getFileByUrl(url);

        if (file == null) {
            throw new ResourceNotFoundException(
                    label + " upload was not found"
            );
        }

        String format = file.getFormat() == null
                ? ""
                : file.getFormat().toLowerCase(Locale.ROOT);

        if (!Set.of("pdf", "jpg", "jpeg", "png").contains(format)) {
            throw new InvalidInputException(
                    label + " must be a PDF, JPG or PNG file"
            );
        }

        long maximumSize = 5L * 1024 * 1024;

        if (file.getFileSizeBytes() == null
                || file.getFileSizeBytes() <= 0
                || file.getFileSizeBytes() > maximumSize) {
            throw new InvalidInputException(
                    label + " must be a non-empty file no larger than 5 MB"
            );
        }

        return url;
    }

    @Transactional
    public void changePassword(PasswordChangeRequest request) {
        log.info("Attempting to change customer password");

        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        validatePassword(request.newPassword());

        User user = findCustomerUserByEmail(getCurrentPrincipalEmail());

        userService.changeUserPassword(
                user.getId(),
                UserType.CUSTOMER,
                request
        );

        log.info(
                "Customer password changed successfully for user ID: {}",
                user.getId()
        );
    }

    public CustomerForgotPasswordResponse forgotPassword(
            CustomerForgotPasswordRequest request
    ) {
        log.info(
                "Processing forgot password request for email: {}",
                request.emailAddress()
        );

        User user = findCustomerUserByEmail(request.emailAddress());
        Customer customer = findCustomerByUserId(user.getId());

        if (!user.isActive() || !customer.isActive()) {
            log.info(
                    "Forgot password failed: Account inactive for email: {}",
                    user.getEmailAddress()
            );

            throw new InvalidOperationException(
                    "Customer account is inactive"
            );
        }

        if (!user.isVerified()) {
            log.info(
                    "Forgot password failed: Account not verified for email: {}",
                    user.getEmailAddress()
            );

            throw new InvalidOperationException(
                    "Customer account has not been verified"
            );
        }

        Map<String, String> otp = userOtpService.generateOtpForUser(
                user.getId(),
                UserOtpType.PASSWORD_RESET
        );

        emailSenderService.sendPasswordResetEmail(
                user.getEmailAddress(),
                user.getId(),
                otp.get("otp"),
                buildFullName(user)
        );

        log.info(
                "Forgot password OTP sent successfully for email: {}",
                user.getEmailAddress()
        );

        return new CustomerForgotPasswordResponse(
                user.getId(),
                user.getEmailAddress(),
                otp.get("expiresAt")
        );
    }

    @Transactional
    public void resetPassword(CustomerResetPasswordRequest request) {
        log.info(
                "Processing password reset for email: {}",
                request != null ? request.emailAddress() : "null"
        );

        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        User user = findCustomerUserByEmail(request.emailAddress());
        Customer customer = findCustomerByUserId(user.getId());

        if (!user.isActive() || !customer.isActive()) {
            log.info(
                    "Reset password failed: Account inactive for email: {}",
                    user.getEmailAddress()
            );

            throw new InvalidOperationException(
                    "Customer account is inactive"
            );
        }

        validatePassword(request.newPassword());

        userService.resetPassword(
                new UserResetPasswordRequest(
                        request.otp(),
                        request.newPassword(),
                        user.getEmailAddress()
                )
        );

        log.info(
                "Customer password reset successfully for email: {}",
                user.getEmailAddress()
        );
    }

    @Transactional
    public void deleteCurrentAccount() {
        log.info("Processing current account deletion");

        User user = findCustomerUserByEmail(getCurrentPrincipalEmail());
        Customer customer = findCustomerByUserId(user.getId());

        softDelete(customer, user);

        log.info(
                "Customer account deleted successfully for user ID: {}",
                user.getId()
        );
    }

    public PaginatedData getCustomers(
            int page,
            int size,
            String searchParam
    ) {
        log.info(
                "Fetching customers list. Page: {}, Size: {}, Search: {}",
                page,
                size,
                searchParam
        );

        validatePagination(page, size);

        Criteria criteria = Criteria.where("deleted").is(false);

        if (StringUtils.hasText(searchParam)) {
            Query userQuery = new Query();

            userQuery.addCriteria(
                    new Criteria().andOperator(
                            Criteria.where("userType")
                                    .is(UserType.CUSTOMER),
                            Criteria.where("isDeleted").is(false),
                            new Criteria().orOperator(
                                    Criteria.where("firstName")
                                            .regex(searchParam, "i"),
                                    Criteria.where("lastName")
                                            .regex(searchParam, "i"),
                                    Criteria.where("emailAddress")
                                            .regex(searchParam, "i"),
                                    Criteria.where("phoneNumber")
                                            .regex(searchParam, "i")
                            )
                    )
            );

            List<String> matchingUserIds =
                    userService.findUsers(userQuery)
                            .stream()
                            .map(User::getId)
                            .toList();

            criteria = criteria.andOperator(
                    new Criteria().orOperator(
                            Criteria.where("customerNumber")
                                    .regex(searchParam, "i"),
                            Criteria.where("userId")
                                    .in(matchingUserIds)
                    )
            );
        }

        return executeCustomerPagination(page, size, criteria);
    }

    public PaginatedData filterCustomers(
            int page,
            int size,
            CustomerFilterRequest filter
    ) {
        log.info(
                "Filtering customers. Page: {}, Size: {}",
                page,
                size
        );

        validatePagination(page, size);

        CustomerFilterRequest request = filter == null
                ? new CustomerFilterRequest(
                null, null, null, null, null, null, null
        )
                : filter;

        validateCustomerFilterDates(request);

        Criteria criteria = Criteria.where("deleted").is(false);

        if (request.marketingEmailsEnabled() != null) {
            criteria = criteria.and("marketingEmailsEnabled")
                    .is(request.marketingEmailsEnabled());
        }

        if (request.registeredFrom() != null) {
            criteria = criteria.and("createdAt")
                    .gte(request.registeredFrom().atStartOfDay());
        }

        if (request.registeredTo() != null) {
            criteria = criteria.and("createdAt")
                    .lt(
                            request.registeredTo()
                                    .plusDays(1)
                                    .atStartOfDay()
                    );
        }

        boolean needsUserFilter = request.active() != null
                || request.verified() != null
                || request.gender() != null
                || StringUtils.hasText(request.searchParam());

        if (needsUserFilter) {
            Query userQuery = new Query();

            Criteria userCriteria = Criteria.where("userType")
                    .is(UserType.CUSTOMER)
                    .and("isDeleted")
                    .is(false);

            if (request.active() != null) {
                userCriteria = userCriteria.and("isActive")
                        .is(request.active());
            }

            if (request.verified() != null) {
                userCriteria = userCriteria.and("isVerified")
                        .is(request.verified());
            }

            if (request.gender() != null) {
                userCriteria = userCriteria.and("gender")
                        .is(request.gender());
            }

            if (StringUtils.hasText(request.searchParam())) {
                userCriteria = userCriteria.andOperator(
                        new Criteria().orOperator(
                                Criteria.where("firstName")
                                        .regex(
                                                request.searchParam(),
                                                "i"
                                        ),
                                Criteria.where("lastName")
                                        .regex(
                                                request.searchParam(),
                                                "i"
                                        ),
                                Criteria.where("emailAddress")
                                        .regex(
                                                request.searchParam(),
                                                "i"
                                        ),
                                Criteria.where("phoneNumber")
                                        .regex(
                                                request.searchParam(),
                                                "i"
                                        )
                        )
                );
            }

            userQuery.addCriteria(userCriteria);

            List<String> matchingUserIds =
                    userService.findUsers(userQuery)
                            .stream()
                            .map(User::getId)
                            .toList();

            criteria = criteria.and("userId").in(matchingUserIds);
        }

        return executeCustomerPagination(page, size, criteria);
    }

    private PaginatedData executeCustomerPagination(
            int page,
            int size,
            Criteria criteria
    ) {
        Query query = new Query(criteria).with(
                PageRequest.of(
                        page - 1,
                        size,
                        Sort.by(
                                Sort.Order.desc("createdAt"),
                                Sort.Order.asc("id")
                        )
                )
        );

        long total = mongoTemplate.count(query, Customer.class);

        List<Customer> customers =
                mongoTemplate.find(query, Customer.class);

        List<String> userIds = customers.stream()
                .map(Customer::getUserId)
                .distinct()
                .toList();

        Map<String, User> usersById = userIds.isEmpty()
                ? Map.of()
                : userService.getUsersByIds(new HashSet<>(userIds))
                .stream()
                .filter(user ->
                        user.getUserType() == UserType.CUSTOMER
                                && !user.isDeleted()
                )
                .collect(
                        Collectors.toMap(
                                User::getId,
                                Function.identity()
                        )
                );

        List<CustomerResponse> responses = customers.stream()
                .map(customer -> {
                    User user = usersById.get(customer.getUserId());

                    if (user == null) {
                        log.info(
                                "Data inconsistency: Customer account not found for customer ID: {}",
                                customer.getId()
                        );

                        throw new ResourceNotFoundException(
                                "Customer account not found"
                        );
                    }

                    return CustomerResponse.from(customer, user);
                })
                .toList();

        return utilsService.createPaginatedData(
                responses,
                page,
                size,
                total
        );
    }

    public CustomerResponse getCustomerById(String customerId) {
        log.info("Fetching customer by ID: {}", customerId);

        Customer customer = findCustomerById(customerId);
        User user = findCustomerUserById(customer.getUserId());

        return CustomerResponse.from(customer, user);
    }

    @Transactional
    public CustomerResponse activateCustomer(String customerId) {
        log.info("Attempting to activate customer ID: {}", customerId);

        Customer customer = findCustomerById(customerId);
        User user = findCustomerUserById(customer.getUserId());

        if (!user.isVerified()) {
            log.info(
                    "Activation failed: Customer account not verified for ID: {}",
                    customerId
            );

            throw new InvalidOperationException(
                    "Unverified customer accounts cannot be activated"
            );
        }

        customer.setActive(true);
        customer.setUpdatedAt(LocalDateTime.now());

        user.setActive(true);
        user.setUpdatedAt(LocalDateTime.now());

        log.info(
                "Customer activated successfully for ID: {}",
                customerId
        );

        return CustomerResponse.from(
                customerRepository.save(customer),
                userService.updateUser(user)
        );
    }

    @Transactional
    public CustomerResponse deactivateCustomer(String customerId) {
        log.info("Attempting to deactivate customer ID: {}", customerId);

        Customer customer = findCustomerById(customerId);
        User user = findCustomerUserById(customer.getUserId());

        customer.setActive(false);
        customer.setUpdatedAt(LocalDateTime.now());

        user.setActive(false);
        user.setUpdatedAt(LocalDateTime.now());

        log.info(
                "Customer deactivated successfully for ID: {}",
                customerId
        );

        return CustomerResponse.from(
                customerRepository.save(customer),
                userService.updateUser(user)
        );
    }

    @Transactional
    public void deleteCustomer(String customerId) {
        log.info("Attempting to delete customer ID: {}", customerId);

        Customer customer = findCustomerById(customerId);
        User user = findCustomerUserById(customer.getUserId());

        softDelete(customer, user);

        log.info(
                "Customer deleted successfully for ID: {}",
                customerId
        );
    }

    private void softDelete(Customer customer, User user) {
        LocalDateTime deletedAt = LocalDateTime.now();

        customer.setDeleted(true);
        customer.setActive(false);
        customer.setDeletedAt(deletedAt);
        customer.setUpdatedAt(deletedAt);

        user.setDeleted(true);
        user.setActive(false);
        user.setDeletedAt(deletedAt);
        user.setUpdatedAt(deletedAt);

        customerRepository.save(customer);
        userService.updateUser(user);

        fileUploadService.deleteFileAsync(user.getProfilePictureUrl());
    }

    private Customer findCustomerById(String customerId) {
        if (customerId == null || customerId.isBlank()) {
            log.info("Validation failed: Customer ID is required");

            throw new InvalidInputException("Customer ID is required");
        }

        return customerRepository
                .findByIdAndDeletedFalse(customerId.trim())
                .orElseThrow(() -> {
                    log.info(
                            "Customer lookup failed: Customer not found for ID: {}",
                            customerId
                    );

                    return new ResourceNotFoundException(
                            "Customer not found"
                    );
                });
    }

    private Customer findCustomerByUserId(String userId) {
        return customerRepository
                .findByUserIdAndDeletedFalse(userId)
                .orElseThrow(() -> {
                    log.info(
                            "Customer lookup failed: Profile not found for user ID: {}",
                            userId
                    );

                    return new ResourceNotFoundException(
                            "Customer profile not found"
                    );
                });
    }

    private User findCustomerUserByEmail(String emailAddress) {
        return userService.findByEmailAndUserType(
                        normalizeEmail(emailAddress),
                        UserType.CUSTOMER
                )
                .orElseThrow(() -> {
                    log.info(
                            "Customer lookup failed: Account not found for email: {}",
                            emailAddress
                    );

                    return new ResourceNotFoundException(
                            "Customer account not found"
                    );
                });
    }

    private User findCustomerUserById(String userId) {
        User user = userService.findById(userId);

        if (user.getUserType() != UserType.CUSTOMER || user.isDeleted()) {
            log.info(
                    "Customer lookup failed: User is not a valid active customer for ID: {}",
                    userId
            );

            throw new ResourceNotFoundException(
                    "Customer account not found"
            );
        }

        return user;
    }

    private Map<String, Object> buildJwtClaims(
            Customer customer,
            User user
    ) {
        Map<String, Object> claims = new HashMap<>();

        claims.put("userId", user.getId());
        claims.put("customerId", customer.getId());
        claims.put("email", user.getEmailAddress());
        claims.put("userType", UserType.CUSTOMER.name());
        claims.put("userRole", "CUSTOMER");
        claims.put("auth", List.of("ROLE_CUSTOMER"));

        return claims;
    }

    private String generateCustomerNumber() {
        String customerNumber;

        do {
            customerNumber = "HLT-CUS-"
                    + UUID.randomUUID()
                    .toString()
                    .substring(0, 8)
                    .toUpperCase(Locale.ROOT);
        } while (
                customerRepository.existsByCustomerNumber(customerNumber)
        );

        return customerNumber;
    }

    private String getCurrentPrincipalEmail() {
        var authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication.getPrincipal() == null) {
            log.info(
                    "Security context validation failed: Authentication is required"
            );

            throw new UnauthorizedException("Authentication is required");
        }

        return authentication.getName();
    }

    private String normalizeEmail(String emailAddress) {
        if (emailAddress == null || emailAddress.isBlank()) {
            log.info("Validation failed: Email address is required");

            throw new InvalidInputException(
                    "Email address is required"
            );
        }

        return emailAddress.trim().toLowerCase(Locale.ROOT);
    }

    private String buildFullName(User user) {
        return (
                safeText(user.getFirstName())
                        + " "
                        + safeText(user.getLastName())
        ).trim();
    }

    private String calculateAge(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return "";
        }

        return Period.between(
                dateOfBirth,
                LocalDate.now()
        ).getYears() + " years";
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            log.info(
                    "Password validation failed: Password must contain at least 8 characters"
            );

            throw new InvalidPasswordException(
                    "Password must contain at least 8 characters"
            );
        }
    }

    private void validatePagination(int page, int size) {
        if (page < 1) {
            log.info(
                    "Pagination validation failed: Page number must be at least 1"
            );

            throw new InvalidInputException(
                    "Page number must be at least 1"
            );
        }
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            log.info("Required text validation failed: {}", message);

            throw new InvalidInputException(message);
        }

        return value.trim();
    }

    private String clean(String value) {
        if (value == null) {
            return null;
        }

        String cleaned = value.trim();

        return cleaned.isBlank() ? null : cleaned;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private void validateCustomerFilterDates(
            CustomerFilterRequest filter
    ) {
        if (filter.registeredFrom() != null
                && filter.registeredTo() != null
                && filter.registeredFrom()
                .isAfter(filter.registeredTo())) {

            log.info(
                    "Date validation failed: Registration start date is after end date"
            );

            throw new InvalidInputException(
                    "Registration start date cannot be after the end date"
            );
        }

        if (LocalDate.MAX.equals(filter.registeredTo())) {
            log.info(
                    "Date validation failed: Registration end date is out of range"
            );

            throw new InvalidInputException(
                    "Registration end date is out of range"
            );
        }
    }
}