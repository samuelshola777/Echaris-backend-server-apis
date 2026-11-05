package com.echaris.api.admin;

import com.echaris.api.admin.request.*;
import com.echaris.api.admin.response.AdminAuthResponse;
import com.echaris.api.admin.response.AdminForgotPasswordResponse;
import com.echaris.api.admin.response.AdminResponse;
import com.echaris.api.userauthmgt.user.User;
import com.echaris.api.userauthmgt.user.UserService;
import com.echaris.api.userauthmgt.user.UserType;
import com.echaris.api.userauthmgt.user.request.PasswordChangeRequest;
import com.echaris.api.utils.PaginatedData;
import com.echaris.api.utils.PaginationService;
import com.echaris.api.utils.appsecurity.JwtService;
import com.echaris.api.utils.exception.*;
import com.echaris.api.utils.fileupload.FileUploadService;
import lombok.RequiredArgsConstructor;


import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import lombok.extern.log4j.Log4j2;
import org.bson.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Log4j2
@RequiredArgsConstructor
public class AdminService {

    private final org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;


     private final PaginationService paginationService;
    private final AdminRepository adminRepository;
    private final UserService userService;
    private final JwtService jwtService;
    private final FileUploadService fileUploadService;

    public AdminResponse createFirstAdmin(AdminCreateRequest request) {
        validateCreateRequest(request);
        if (adminRepository.countByDeleted(false) > 0) {
            throw new InvalidOperationException("First admin already exists");
        }
        if (request.firstName().trim().length() > 80 || request.lastName().trim().length() > 80 || request.emailAddress().length() > 254
                || !request.emailAddress().trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new InvalidInputException("Valid names and email are required");
        User user = userService.createAdminUserFromAdminModule(
                clean(request.firstName()),
                clean(request.lastName()),
                cleanEmail(request.emailAddress()),
                request.password(),
                clean(request.phoneNumber()),
                clean(request.profilePictureUrl()),
                clean(request.profileColourCode()),
                request.gender(),
                true
        );

        Admin admin = Admin.builder()
                .userId(user.getId())
                .active(true)
                .deleted(false)
                .lastLoginAt(null)
                .deletedAt(null)
                .build();

        Admin savedAdmin = adminRepository.save(admin);
        return mapToResponse(savedAdmin, user);
    }

    @Transactional
    public AdminResponse createAdmin(AdminCreateRequest request) {
        requireSuperAdmin();
        validateCreateRequest(request);

        if (request.firstName().trim().length() > 80 || request.lastName().trim().length() > 80 || request.emailAddress().length() > 254
                || !request.emailAddress().trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
            throw new InvalidInputException("Valid names and email are required");
        User user = userService.createAdminUserFromAdminModule(
                clean(request.firstName()),
                clean(request.lastName()),
                cleanEmail(request.emailAddress()),
                request.password(),
                clean(request.phoneNumber()),
                clean(request.profilePictureUrl()),
                clean(request.profileColourCode()),
                request.gender(),
                true
        );

        if (adminRepository.existsByUserId(user.getId())) {
            throw new ResourceExistsException("User is already an admin");
        }

        Admin admin = Admin.builder()
                .userId(user.getId())
                .active(true)
                .deleted(false)
                .lastLoginAt(null)
                .deletedAt(null)
                .build();

        Admin savedAdmin = adminRepository.save(admin);
        return mapToResponse(savedAdmin, user);
    }

    public AdminAuthResponse login(AdminLoginRequest request) {
        if (request == null || isBlank(request.emailAddress()) || isBlank(request.password())) {
            throw new InvalidInputException("Email address and password are required");
        }
        User user = userService.authenticateAdminUserFromAdminModule(
                cleanEmail(request.emailAddress()),
                request.password()
        );
        Admin admin = adminRepository.findByUserIdAndDeleted(user.getId(), false)
                .orElseThrow(() -> new UnauthorizedException("Admin account not found"));

        if (!admin.isActive()) {
            throw new UnauthorizedException("Admin account is inactive");
        }
        admin.setLastLoginAt(LocalDateTime.now());
        Admin savedAdmin = adminRepository.save(admin);
        Map<String, Object> claims = buildJwtClaims(savedAdmin, user);
        String accessToken = jwtService.generateToken(user.getEmailAddress(), claims);
        String refreshToken = jwtService.generateRefreshToken(user.getEmailAddress(), claims);

        return new AdminAuthResponse(
                accessToken,
                refreshToken,
                mapToResponse(savedAdmin, user)
        );
    }

    public AdminResponse getCurrentAdminProfile() {
        String emailAddress = getCurrentPrincipalEmail();

        User user = userService.getAdminUserByEmailFromAdminModule(emailAddress);

        Admin admin = adminRepository.findByUserIdAndDeleted(user.getId(), false)
                .orElseThrow(() -> new ResourceNotFoundException("Admin profile not found"));

        if (!admin.isActive() || !user.isActive() || !user.isVerified() || user.isDeleted()) {
            throw new UnauthorizedException("Admin account is inactive or unavailable");
        }
        return mapToResponse(admin, user);
    }

    public List<AdminResponse> getAdmins() {
        requireSuperAdmin();
        return adminRepository.findAllByDeleted(false)
                .stream()
                .map(admin -> mapToResponse(admin, userService.getAdminUserByIdFromAdminModule(admin.getUserId())))
                .toList();
    }

    public AdminResponse getAdminById(String adminId) {
        requireSuperAdmin();
        Admin admin = findAdminById(adminId);
        User user = userService.getAdminUserByIdFromAdminModule(admin.getUserId());

        return mapToResponse(admin, user);
    }

    @Transactional
    public AdminResponse updateAdmin(String adminId, AdminUpdateRequest request) {
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        if ((request.firstName() != null && request.firstName().isBlank()) || (request.lastName() != null && request.lastName().isBlank())) {
            throw new InvalidInputException("Names cannot be blank");
        }
        Admin admin = findAdminById(adminId);
        AdminResponse actor = getCurrentAdminProfile();
        if (!actor.adminId().equals(adminId)) {
            requireSuperAdmin();
            requireManageable(admin);
        }
        User oldUser = userService.getAdminUserByIdFromAdminModule(admin.getUserId());

        String oldProfilePictureUrl = oldUser.getProfilePictureUrl();

        User updatedUser = userService.updateAdminUserFromAdminModule(admin.getUserId(), request);


        admin.setUpdatedAt(LocalDateTime.now());

        Admin savedAdmin = adminRepository.save(admin);

        fileUploadService.deleteOldFileIfChanged(oldProfilePictureUrl, updatedUser.getProfilePictureUrl());
        adminRepository.save(admin);
        userService.save(updatedUser);
        return mapToResponse(savedAdmin, updatedUser);
    }

    @Transactional
    public AdminResponse activateAdmin(String adminId) {
        requireSuperAdmin();
        Admin admin = findAdminById(adminId);
        requireManageable(admin);

        admin.setActive(true);
        admin.setUpdatedAt(LocalDateTime.now());

        Admin savedAdmin = adminRepository.save(admin);

        User user = userService.toggleAdminUserActiveStatusFromAdminModule(admin.getUserId(), true);
        return mapToResponse(savedAdmin, user);
    }

    @Transactional
    public AdminResponse deactivateAdmin(String adminId) {
        requireSuperAdmin();
        Admin admin = findAdminById(adminId);
        requireManageable(admin);
        admin.setActive(false);
        admin.setUpdatedAt(LocalDateTime.now());
        Admin savedAdmin = adminRepository.save(admin);
        User user = userService.toggleAdminUserActiveStatusFromAdminModule(admin.getUserId(), false);
        return mapToResponse(savedAdmin, user);
    }

    @Transactional
    public void deleteAdmin(String adminId) {
        requireSuperAdmin();
        Admin admin = findAdminById(adminId);
        requireManageable(admin);
        User user = userService.getAdminUserByIdFromAdminModule(admin.getUserId());
        String profilePictureUrl = user.getProfilePictureUrl();
        admin.setDeleted(true);
        admin.setActive(false);
        admin.setDeletedAt(LocalDateTime.now());
        admin.setUpdatedAt(LocalDateTime.now());
        Admin savedAdmin = adminRepository.save(admin);
        userService.softDeleteAdminUserFromAdminModule(admin.getUserId());
        fileUploadService.deleteFileAsync(profilePictureUrl);
    }

    public long countAllAdmins() {
        return adminRepository.countByDeleted(false);
    }

    public long countActiveAdmins() {
        return adminRepository.countByDeletedAndActive(false, true);
    }

    public long countInactiveAdmins() {
        return adminRepository.countByDeletedAndActive(false, false);
    }

    private Admin findAdminById(String adminId) {
        if (isBlank(adminId)) {
            throw new ResourceNotFoundException("Admin not found");
        }

        return adminRepository.findByIdAndDeleted(adminId, false)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found"));
    }

    private Map<String, Object> buildJwtClaims(Admin admin, User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("passwordVersion", passwordVersion(user));
        claims.put("adminId", admin.getId());
        claims.put("email", user.getEmailAddress());
        claims.put("userType", "ADMIN");
        claims.put("userRole", admin.isSuperAdmin() ? "SUPER_ADMIN" : "ADMIN");
        claims.put("auth", admin.isSuperAdmin()
                ? List.of("ROLE_ADMIN", "ROLE_SUPER_ADMIN") : List.of("ROLE_ADMIN"));
        return claims;
    }

    private AdminResponse mapToResponse(Admin admin, User user) {
        return new AdminResponse(
                admin.getId(),
                admin.getUserId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmailAddress(),
                user.getPhoneNumber(),
                user.getProfilePictureUrl(),
                user.getDateOfBirth(),
                user.getGender(),
                user.getAge(),
                user.getUserType(),
                admin.isActive(),
                user.isActive(),
                user.isVerified(),
                admin.getCreatedAt(),
                admin.getUpdatedAt(),
                admin.getLastLoginAt(),
                admin.isSuperAdmin()
        );
    }

    private void validateCreateRequest(AdminCreateRequest request) {
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        if (isBlank(request.firstName())) {
            throw new InvalidInputException("First name is required");
        }

        if (isBlank(request.lastName())) {
            throw new InvalidInputException("Last name is required");
        }

        if (isBlank(request.emailAddress())) {
            throw new InvalidInputException("Email address is required");
        }

        if (isBlank(request.password())) {
            throw new InvalidInputException("Password is required");
        }

        if (request.password().length() < 12 || request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new InvalidInputException("Password must be at least 12 characters and at most 72 UTF-8 bytes");
        }
    }


    private String buildFullName(User user) {
        if (user == null) {
            return "Unknown admin";
        }

        String firstName = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String lastName = user.getLastName() == null ? "" : user.getLastName().trim();

        String fullName = (firstName + " " + lastName).trim();

        return fullName.isBlank() ? user.getEmailAddress() : fullName;
    }

    private String getCurrentPrincipalEmail() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal == null) {
            throw new UnauthorizedException("Authentication is required");
        }

        return principal.toString();
    }

    public AdminForgotPasswordResponse initiateAdminForgotPassword(AdminForgotPasswordRequest request) {
        if (request == null || isBlank(request.emailAddress())) {
            throw new InvalidInputException("Email address is required");
        }

        User user = userService.getAdminUserByEmailFromAdminModule(
                cleanEmail(request.emailAddress())
        );

        Admin admin = adminRepository.findByUserIdAndDeleted(user.getId(), false)
                .orElseThrow(() -> new ResourceNotFoundException("Admin account not found"));

        if (!admin.isActive() || !user.isActive()) {
            throw new InvalidOperationException("Admin account is not active");
        }

        if (!user.isVerified()) {
            throw new InvalidOperationException("Admin account is not verified");
        }

        Map<String, String> otp = userService.generatePasswordResetOtpForAdmin(user.getId());

        return new AdminForgotPasswordResponse(
                user.getId(),
                user.getEmailAddress(),
                otp.get("otp"),
                otp.get("expiresAt")
        );
    }

    @Transactional
    public void resetAdminPassword(AdminResetPasswordRequest request) {
        if (request == null) {
            throw new InvalidInputException("Request body is required");
        }

        if (isBlank(request.emailAddress())) {
            throw new InvalidInputException("Email address is required");
        }

        if (isBlank(request.otp())) {
            throw new InvalidInputException("OTP is required");
        }

        if (isBlank(request.newPassword())) {
            throw new InvalidInputException("New password is required");
        }

        if (request.newPassword().length() < 6) {
            throw new InvalidInputException("Password must be at least 12 characters and at most 72 UTF-8 bytes");
        }

        User user = userService.getAdminUserByEmailFromAdminModule(
                cleanEmail(request.emailAddress())
        );

        Admin admin = adminRepository.findByUserIdAndDeleted(user.getId(), false)
                .orElseThrow(() -> new ResourceNotFoundException("Admin account not found"));

        if (!admin.isActive() || !user.isActive()) {
            throw new InvalidOperationException("Admin account is not active");
        }

        if (!user.isVerified()) {
            throw new InvalidOperationException("Admin account is not verified");
        }

        userService.resetAdminPasswordFromAdminModule(
                user.getId(),
                request.otp(),
                request.newPassword()
        );

    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String cleanEmail(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public void requireSuperAdmin() {
        if (!getCurrentAdminProfile().superAdmin()) throw new UnauthorizedException("Super admin access is required");
    }

    private void requireManageable(Admin target) {
        if (target.isSuperAdmin() || target.getId().equals(getCurrentAdminProfile().adminId())) {
            throw new InvalidOperationException("Cannot manage your own access or a super admin through this endpoint");
        }
    }

    public String passwordVersion(User user) {
        return user.getPasswordChangedAt() == null ? "INITIAL" : user.getPasswordChangedAt().toString();
    }

    public boolean isCurrentToken(io.jsonwebtoken.Claims claims) {
        User user = userService.getAdminUserByEmailFromAdminModule(claims.getSubject());
        AdminResponse profile = getCurrentAdminProfile();
        Object authorities = claims.get("auth");
        return passwordVersion(user).equals(claims.get("passwordVersion", String.class))
                && authorities instanceof List<?> roles
                && profile.superAdmin() == roles.contains("ROLE_SUPER_ADMIN");
    }

    @Transactional
    public AdminResponse updateMyProfile(AdminUpdateRequest request) {
        return updateAdmin(getCurrentAdminProfile().adminId(), request);
    }

    @Transactional
    public void changeMyPassword(PasswordChangeRequest request) {
        AdminResponse actor = getCurrentAdminProfile();
        User user = userService.getAdminUserByIdFromAdminModule(actor.userId());
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        if (!encoder.matches(request.oldPassword(), user.getPassword()))
            throw new InvalidInputException("Current password is incorrect");
        if (request.newPassword().length() < 12 || request.newPassword().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new InvalidInputException("Password must be at least 12 characters and at most 72 UTF-8 bytes");
        }
        if (encoder.matches(request.newPassword(), user.getPassword()))
            throw new InvalidInputException("Choose a different password");
        user.setPassword(encoder.encode(request.newPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userService.updateUser(user);

    }



    public PaginatedData getAdminPage(int page, int size) {
        requireSuperAdmin();
        PaginatedData result = paginationService.paginate(new Query(Criteria.where("deleted").is(false)), Admin.class,
                page, size, "createdAt", "DESC", Set.of("createdAt"));
        List<?> rows = (List<?>) result.data();
        List<AdminResponse> profiles = rows.stream().map(row -> (Admin) row)
                .map(row -> mapToResponse(row, userService.getAdminUserByIdFromAdminModule(row.getUserId()))).toList();
        return new PaginatedData(result.totalPage(), result.numberOfElements(), result.totalElements(), profiles);
    }

}