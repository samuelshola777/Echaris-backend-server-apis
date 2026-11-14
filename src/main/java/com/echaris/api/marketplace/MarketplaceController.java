package com.echaris.api.marketplace;

import com.echaris.api.marketplace.request.MarketplaceFinancingRequest;
import com.echaris.api.marketplace.request.MarketplaceFulfillmentRequest;
import com.echaris.api.marketplace.request.MarketplaceFulfillmentStatusRequest;
import com.echaris.api.marketplace.request.MarketplaceRateRequest;
import com.echaris.api.marketplace.response.MarketplaceFinancingResponse;
import com.echaris.api.userPayment.UserPayment;
import com.echaris.api.userPayment.UserPaymentStatus;
import com.echaris.api.utils.ApiResponse;
import com.echaris.api.utils.PaginatedData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/marketplace")
public class MarketplaceController {

    private final MarketplaceFinancingService financingService;
    private final MarketplaceRateService rateService;

    @PostMapping("/private/create-financing")
    public ApiResponse<MarketplaceFinancing> createFinancing(
            @Valid @RequestBody MarketplaceFinancingRequest request
    ) {
        return new ApiResponse<>(true, "Marketplace financing created",
                financingService.createFinancing(request));
    }

    @GetMapping("/private/get-my-financings")
    public ApiResponse<PaginatedData> getMyFinancings(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return new ApiResponse<>(true, "Marketplace financing retrieved",
                financingService.getMyFinancings(page, size));
    }

    @GetMapping({"/private/get-my-financing/{financingId}", "/private/{financingId}"})
    public ApiResponse<MarketplaceFinancingResponse> getMyFinancing(
            @PathVariable("financingId") String financingId
    ) {
        return new ApiResponse<>(true, "Marketplace financing retrieved",
                financingService.getMyFinancing(financingId));
    }

    @GetMapping("/private/get-my-returns/{financingId}")
    public ApiResponse<PaginatedData> getMyReturns(
            @PathVariable("financingId") String financingId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return new ApiResponse<>(true, "Marketplace returns retrieved",
                financingService.getMyReturns(financingId, page, size));
    }

    @PostMapping("/private/make-payment-for-market-financing/{financingId}/{userPaymentId}")
    public ApiResponse<MarketplaceFinancing> makePaymentForMarketFinancing(
            @PathVariable("financingId") String financingId,
            @PathVariable("userPaymentId") String userPaymentId
    ) {
        return new ApiResponse<>(true, "Marketplace payment submitted for review",
                financingService.makePaymentForMarketFinancing(financingId, userPaymentId));
    }

    @PutMapping("/private/update-fulfillment/{financingId}")
    public ApiResponse<MarketplaceFinancing> updateFulfillment(
            @PathVariable("financingId") String financingId,
            @Valid @RequestBody MarketplaceFulfillmentRequest request
    ) {
        return new ApiResponse<>(true, "Fulfillment details updated",
                financingService.updateFulfillment(financingId, request));
    }

    @PostMapping("/private/request-capital-payout/{financingId}")
    public ApiResponse<MarketplaceFinancing> requestCapitalPayout(
            @PathVariable("financingId") String financingId
    ) {
        return new ApiResponse<>(true, "Capital payout requested",
                financingService.requestCapitalPayout(financingId));
    }

    @GetMapping("/private/admin/get-admin-financings")
    public ApiResponse<PaginatedData> getAdminFinancings(
            @RequestParam(name = "status", required = false) MarketplaceFinancingStatus status,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return new ApiResponse<>(true, "Marketplace financings retrieved",
                financingService.getAdminFinancings(status, search, page, size));
    }

    @GetMapping({
            "/private/admin/get-admin-financing/{financingId}",
            "/private/admin/{financingId}"
    })
    public ApiResponse<MarketplaceFinancingResponse> getAdminFinancing(
            @PathVariable("financingId") String financingId
    ) {
        return new ApiResponse<>(true, "Marketplace financing retrieved",
                financingService.getAdminFinancing(financingId));
    }

    @GetMapping("/private/admin/get-admin-returns/{financingId}")
    public ApiResponse<PaginatedData> getAdminReturns(
            @PathVariable("financingId") String financingId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return new ApiResponse<>(true, "Marketplace returns retrieved",
                financingService.getAdminReturns(financingId, page, size));
    }

    @PutMapping("/private/admin/update-fulfillment-status/{financingId}")
    public ApiResponse<MarketplaceFinancing> updateFulfillmentStatus(
            @PathVariable("financingId") String financingId,
            @Valid @RequestBody MarketplaceFulfillmentStatusRequest request
    ) {
        return new ApiResponse<>(true, "Fulfillment status updated",
                financingService.updateFulfillmentStatus(financingId, request));
    }

    @PutMapping("/private/admin/update-market-financing-payment-status/{userPaymentId}")
    public ApiResponse<UserPayment> adminUpdatesMarketFinancingPaymentStatus(
            @PathVariable("userPaymentId") String userPaymentId,
            @RequestParam("status") UserPaymentStatus status,
            @RequestParam(value = "reason", required = false) String reason
    ) {

        return new ApiResponse<>(true, "Marketplace payment status updated",
                financingService.adminUpdatesMarketFinancingPaymentStatus(userPaymentId, status, reason));
    }

    @GetMapping("/private/admin/get-rate")
    public ApiResponse<MarketplaceRate> getRate() {
        return new ApiResponse<>(true, "Marketplace rate retrieved", rateService.getRate());
    }

    @PutMapping("/private/admin/update-rate")
    public ApiResponse<MarketplaceRate> updateRate(
            @Valid @RequestBody MarketplaceRateRequest request
    ) {
        return new ApiResponse<>(true, "Marketplace rate saved", rateService.updateRate(request));
    }

    @GetMapping("/private/admin/get-rate-history")
    public ApiResponse<PaginatedData> getRateHistory(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        return new ApiResponse<>(true, "Marketplace rate history retrieved",
                rateService.getRateHistory(page, size));
    }
}