package com.echaris.api.userPayment;

import com.echaris.api.userPayment.request.UserPaymentApprovalRequest;
import com.echaris.api.userPayment.request.UserPaymentRequest;
import com.echaris.api.utils.ApiResponse;
import com.echaris.api.utils.PaginatedData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
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
        return new ApiResponse<>(true, "Payment submitted for review", userPaymentService.submit(request));
    }

    @GetMapping("/private")
    public ApiResponse<PaginatedData> getMyPayments(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(true, "Payments retrieved", userPaymentService.getMyPayments(page, size));
    }

    @GetMapping("/private/admin")
    public ApiResponse<PaginatedData> getAdminPayments(
            @RequestParam(defaultValue = "PENDING") UserPaymentStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(true, "Payment submissions retrieved",
                userPaymentService.getAdminPayments(status, page, size));
    }

    @PutMapping("/private/admin/{paymentId}/approve")
    public ApiResponse<UserPayment> approve(
            @PathVariable String paymentId,
            @Valid @RequestBody UserPaymentApprovalRequest request
    ) {
        return new ApiResponse<>(true, "Payment approved", userPaymentService.approve(paymentId, request));
    }

    @PutMapping("/private/admin/{paymentId}/reject")
    public ApiResponse<UserPayment> reject(@PathVariable String paymentId) {
        return new ApiResponse<>(true, "Payment rejected", userPaymentService.reject(paymentId));
    }
}