package com.echaris.api.userPayment;

import com.echaris.api.payoutaccount.response.CustomerPayoutAccountResponse;
import com.echaris.api.userPayment.request.UserPaymentPayoutRequest;
import com.echaris.api.userPayment.request.UserPaymentRequest;
import com.echaris.api.utils.ApiResponse;
import com.echaris.api.utils.PaginatedData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/user-payments")
public class UserPaymentController {

    private final UserPaymentService userPaymentService;

    @PostMapping("/private")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserPayment> submit(@Valid @RequestBody UserPaymentRequest request) {
        return new ApiResponse<>(true, "Payment submitted for review", userPaymentService.createUserManualPayment(request));
    }

    @GetMapping("/private")
    public ApiResponse<PaginatedData> getMyPayments(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserPaymentStatus status,
            @RequestParam(required = false) UserPaymentDirection direction,
            @RequestParam(required = false) UserPaymentPurpose purpose,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(true, "Payments retrieved",
                userPaymentService.getMyPayments(search, status, direction, purpose, page, size));
    }

    @GetMapping("/private/admin/payout-accounts/{customerUserId}")
    public ApiResponse<CustomerPayoutAccountResponse> getPayoutAccount(@PathVariable String customerUserId) {
        return new ApiResponse<>(true, "Customer payout account retrieved",
                userPaymentService.getPayoutAccount(customerUserId));
    }

    @PostMapping("/private/admin/payouts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserPayment> recordPayout(@Valid @RequestBody UserPaymentPayoutRequest request) {
        return new ApiResponse<>(true, "Customer payout recorded", userPaymentService.recordPayout(request));
    }

    @GetMapping("/private/admin/payouts")
    public ApiResponse<PaginatedData> getAdminPayouts(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserPaymentStatus status,
            @RequestParam(required = false) String customerUserId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(true, "Customer payouts retrieved",
                userPaymentService.getAdminPayments(search, status, null,
                        UserPaymentPurpose.CUSTOMER_PAYOUT, customerUserId, page, size));
    }

    @GetMapping("/private/admin")
    public ApiResponse<PaginatedData> getAdminPayments(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) UserPaymentStatus status,
            @RequestParam(required = false) UserPaymentDirection direction,
            @RequestParam(required = false) UserPaymentPurpose purpose,
            @RequestParam(required = false) String customerUserId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(true, "Payments retrieved",
                userPaymentService.getAdminPayments(search, status, direction, purpose, customerUserId, page, size));
    }

    @GetMapping("/private/admin/{paymentId}")
    public ApiResponse<UserPayment> getAdminPayment(@PathVariable String paymentId) {
        return new ApiResponse<>(true, "Payment details retrieved", userPaymentService.getAdminPayment(paymentId));
    }


}