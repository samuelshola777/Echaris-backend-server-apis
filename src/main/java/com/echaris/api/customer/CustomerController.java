package com.echaris.api.customer;

import com.echaris.api.notification.NotificationResponse;
import com.echaris.api.customer.request.*;
import com.echaris.api.customer.response.CustomerAuthResponse;
import com.echaris.api.customer.response.CustomerForgotPasswordResponse;
import com.echaris.api.customer.response.CustomerKycResponse;
import com.echaris.api.customer.response.CustomerOtpResponse;
import com.echaris.api.customer.response.CustomerResponse;
import com.echaris.api.userauthmgt.user.request.PasswordChangeRequest;
import com.echaris.api.utils.ApiResponse;
import com.echaris.api.utils.PaginatedData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping("/public/register")
    public ResponseEntity<ApiResponse> register(
            @Valid @RequestBody CustomerCreateRequest request
    ) {
        customerService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        new ApiResponse<>(
                                true,
                                "Customer account created. Check your email for the verification OTP",
                                null
                        )
                );
    }

    @PostMapping("/public/verify-otp")
    public ResponseEntity<ApiResponse<CustomerResponse>> verifyAccount(
            @Valid @RequestBody CustomerVerifyOtpRequest request
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Customer account verified successfully",
                        customerService.verifyAccount(request)
                )
        );
    }

    @PostMapping("/public/resend-otp")
    public ResponseEntity<ApiResponse<CustomerOtpResponse>> resendOtp(
            @Valid @RequestBody CustomerResendOtpRequest request
    ) {
        customerService.resendVerificationOtp(request);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Verification OTP sent successfully",
                        null
                )
        );
    }

    @PostMapping("/public/login")
    public ResponseEntity<ApiResponse<CustomerAuthResponse>> login(
            @Valid @RequestBody CustomerLoginRequest request
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Customer login successful",
                        customerService.login(request)
                )
        );
    }

    @PostMapping("/public/forgot-password")
    public ResponseEntity<ApiResponse<CustomerForgotPasswordResponse>> forgotPassword(
            @Valid @RequestBody CustomerForgotPasswordRequest request
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Password reset OTP sent successfully",
                        customerService.forgotPassword(request)
                )
        );
    }

    @PostMapping("/public/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody CustomerResetPasswordRequest request
    ) {
        customerService.resetPassword(request);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Password reset successfully",
                        null
                )
        );
    }

    @GetMapping("/private/account-access")
    public ResponseEntity<ApiResponse<CustomerResponse>> checkAccountAccess(
            @RequestAttribute("validatedProfile")
            CustomerResponse profile
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Account access verified",
                        profile
                )
        );
    }

    @GetMapping("/private/profile")
    public ResponseEntity<ApiResponse<CustomerResponse>> getProfile() {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Customer profile retrieved successfully",
                        customerService.getCurrentProfile()
                )
        );
    }

    @PatchMapping("/private/profile")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateProfile(
            @Valid @RequestBody CustomerUpdateRequest request
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Customer profile updated successfully",
                        customerService.updateCurrentProfile(request)
                )
        );
    }

    @PostMapping("/private/kyc")
    public ResponseEntity<ApiResponse<CustomerKycResponse>> submitKycDocument(
            @Valid @RequestBody CustomerKycRequest request
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "KYC document submitted successfully for review",
                        customerService.submitKycDocument(request)
                )
        );
    }

    @GetMapping("/private/kyc")
    public ResponseEntity<ApiResponse<CustomerKycResponse>> getKycDocuments() {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "KYC documents retrieved successfully",
                        customerService.getCurrentKycDocuments()
                )
        );
    }

    @PatchMapping("/private/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            @Valid @RequestBody PasswordChangeRequest request
    ) {
        customerService.changePassword(request);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Password changed successfully",
                        null
                )
        );
    }

    @DeleteMapping("/private/profile")
    public ResponseEntity<ApiResponse<Void>> deleteCurrentAccount() {
        customerService.deleteCurrentAccount();

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Customer account deleted successfully",
                        null
                )
        );
    }

    @GetMapping("/private/admin")
    public ResponseEntity<ApiResponse<PaginatedData>> getCustomers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(
                    name = "searchParam",
                    required = false
            ) String searchParam
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Customers retrieved successfully",
                        customerService.getCustomers(
                                page,
                                size,
                                searchParam
                        )
                )
        );
    }

    @GetMapping("/private/admin/{customerId}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomerById(
            @PathVariable String customerId
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Customer retrieved successfully",
                        customerService.getCustomerById(customerId)
                )
        );
    }

    @PatchMapping("/private/admin/{customerId}/activate")
    public ResponseEntity<ApiResponse<CustomerResponse>> activateCustomer(
            @PathVariable String customerId
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Customer activated successfully",
                        customerService.activateCustomer(customerId)
                )
        );
    }

    @PatchMapping("/private/admin/{customerId}/deactivate")
    public ResponseEntity<ApiResponse<CustomerResponse>> deactivateCustomer(
            @PathVariable String customerId
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Customer deactivated successfully",
                        customerService.deactivateCustomer(customerId)
                )
        );
    }

    @DeleteMapping("/private/admin/{customerId}")
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(
            @PathVariable String customerId
    ) {
        customerService.deleteCustomer(customerId);

        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Customer deleted successfully",
                        null
                )
        );
    }

    @PostMapping("/private/admin/filter")
    public ResponseEntity<ApiResponse<PaginatedData>> filterCustomers(
            @RequestParam(
                    name = "page",
                    defaultValue = "1"
            ) int page,
            @RequestParam(
                    name = "size",
                    defaultValue = "10"
            ) int size,
            @Valid @ModelAttribute CustomerFilterRequest filter
    ) {
        return ResponseEntity.ok(
                new ApiResponse<>(
                        true,
                        "Customers filtered successfully",
                        customerService.filterCustomers(
                                page,
                                size,
                                filter
                        )
                )
        );
    }

    @GetMapping("/private/admin/kyc")
    public ApiResponse<PaginatedData> getKycSubmissions(
            @RequestParam(defaultValue = "PENDING")
            CustomerKycReviewStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(
                true,
                "KYC submissions retrieved",
                customerService.getKycSubmissions(
                        status,
                        page,
                        size
                )
        );
    }

    @GetMapping("/private/admin/{customerId}/kyc")
    public ApiResponse<CustomerKycResponse> getCustomerKycForAdmin(
            @PathVariable String customerId
    ) {
        return new ApiResponse<>(
                true,
                "KYC documents retrieved",
                customerService.getCustomerKycForAdmin(customerId)
        );
    }

    @PatchMapping(
            "/private/admin/{customerId}/kyc/{documentType}/approve"
    )
    public ApiResponse<CustomerKycResponse> approveCustomerKyc(
            @PathVariable String customerId,
            @PathVariable CustomerKycDocumentType documentType,
            @Valid @RequestBody CustomerKycReviewRequest request
    ) {
        return new ApiResponse<>(
                true,
                "KYC document approved",
                customerService.approveCustomerKyc(
                        customerId,
                        documentType,
                        request
                )
        );
    }

    @PatchMapping(
            "/private/admin/{customerId}/kyc/{documentType}/reject"
    )
    public ApiResponse<CustomerKycResponse> rejectCustomerKyc(
            @PathVariable String customerId,
            @PathVariable CustomerKycDocumentType documentType,
            @Valid @RequestBody CustomerKycReviewRequest request
    ) {
        return new ApiResponse<>(
                true,
                "KYC document rejected",
                customerService.rejectCustomerKyc(
                        customerId,
                        documentType,
                        request
                )
        );
    }

    @GetMapping("/private/notifications")
    public ApiResponse<java.util.List<NotificationResponse>> getMyKycNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(
                true,
                "Notifications retrieved",
                customerService.getMyKycNotifications(
                        page,
                        size
                )
        );
    }

    @PatchMapping("/private/notifications/{notificationId}/read")
    public ApiResponse<NotificationResponse> readMyKycNotification(
            @PathVariable String notificationId
    ) {
        return new ApiResponse<>(
                true,
                "Notification marked as read",
                customerService.readMyKycNotification(notificationId)
        );
    }

    @GetMapping("/private/admin/notifications")
    public ApiResponse<java.util.List<NotificationResponse>> getAdminKycNotifications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(
                true,
                "Notifications retrieved",
                customerService.getAdminKycNotifications(
                        page,
                        size
                )
        );
    }

    @PatchMapping(
            "/private/admin/notifications/{notificationId}/read"
    )
    public ApiResponse<NotificationResponse> readAdminKycNotification(
            @PathVariable String notificationId
    ) {
        return new ApiResponse<>(
                true,
                "Notification marked as read",
                customerService.readAdminKycNotification(notificationId)
        );
    }
}