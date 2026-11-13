package com.echaris.api.marketplace;

import com.echaris.api.marketplace.request.MarketplaceFinancingRequest;
import com.echaris.api.marketplace.request.MarketplaceFulfillmentRequest;
import com.echaris.api.marketplace.request.MarketplaceFulfillmentStatusRequest;
import com.echaris.api.marketplace.request.MarketplaceRateRequest;
import com.echaris.api.marketplace.response.MarketplacePaymentInstructionsResponse;
import com.echaris.api.marketplace.response.MarketplaceTermsResponse;
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

    @GetMapping("/private/terms")
    public ApiResponse<MarketplaceTermsResponse> getTerms() {
        return new ApiResponse<>(true, "Marketplace terms retrieved", financingService.getTerms());
    }

    @PostMapping("/private")
    public ApiResponse<MarketplaceFinancing> createFinancing(@Valid @RequestBody MarketplaceFinancingRequest request) {
        return new ApiResponse<>(true, "Marketplace financing created", financingService.createFinancing(request));
    }

    @GetMapping("/private")
    public ApiResponse<PaginatedData> getMyFinancings(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(true, "Marketplace financing retrieved", financingService.getMyFinancings(page, size));
    }

    @GetMapping("/private/{financingId}")
    public ApiResponse<MarketplaceFinancing> getMyFinancing(@PathVariable String financingId) {
        return new ApiResponse<>(true, "Marketplace financing retrieved", financingService.getMyFinancing(financingId));
    }

    @GetMapping("/private/{financingId}/returns")
    public ApiResponse<PaginatedData> getMyReturns(
            @PathVariable String financingId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(true, "Marketplace returns retrieved", financingService.getMyReturns(financingId, page, size));
    }

    @GetMapping("/private/{financingId}/payment-instructions")
    public ApiResponse<MarketplacePaymentInstructionsResponse> getPaymentInstructions(@PathVariable String financingId) {
        return new ApiResponse<>(true, "Payment instructions retrieved", financingService.getPaymentInstructions(financingId));
    }

    @PutMapping("/private/{financingId}/fulfillment")
    public ApiResponse<MarketplaceFinancing> updateFulfillment(
            @PathVariable String financingId,
            @Valid @RequestBody MarketplaceFulfillmentRequest request
    ) {
        return new ApiResponse<>(true, "Fulfillment details updated", financingService.updateFulfillment(financingId, request));
    }

    @PostMapping("/private/{financingId}/request-capital")
    public ApiResponse<MarketplaceFinancing> requestCapitalPayout(@PathVariable String financingId) {
        return new ApiResponse<>(true, "Capital payout requested", financingService.requestCapitalPayout(financingId));
    }

    @GetMapping("/private/admin")
    public ApiResponse<PaginatedData> getAdminFinancings(
            @RequestParam(required = false) MarketplaceFinancingStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(true, "Marketplace financing retrieved", financingService.getAdminFinancings(status, page, size));
    }

    @GetMapping("/private/admin/{financingId}")
    public ApiResponse<MarketplaceFinancing> getAdminFinancing(@PathVariable String financingId) {
        return new ApiResponse<>(true, "Marketplace financing retrieved", financingService.getAdminFinancing(financingId));
    }

    @GetMapping("/private/admin/{financingId}/returns")
    public ApiResponse<PaginatedData> getAdminReturns(
            @PathVariable String financingId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(true, "Marketplace returns retrieved",
                financingService.getAdminReturns(financingId, page, size));
    }

    @PutMapping("/private/admin/{financingId}/fulfillment-status")
    public ApiResponse<MarketplaceFinancing> updateFulfillmentStatus(
            @PathVariable String financingId,
            @Valid @RequestBody MarketplaceFulfillmentStatusRequest request
    ) {
        return new ApiResponse<>(true, "Fulfillment status updated",
                financingService.updateFulfillmentStatus(financingId, request));
    }

    @GetMapping("/private/admin/rate")
    public ApiResponse<MarketplaceRate> getRate() {
        return new ApiResponse<>(true, "Marketplace rate retrieved", rateService.getRate());
    }

    @PutMapping("/private/admin/rate")
    public ApiResponse<MarketplaceRate> updateRate(@Valid @RequestBody MarketplaceRateRequest request) {
        return new ApiResponse<>(true, "Marketplace rate saved", rateService.updateRate(request));
    }

    @GetMapping("/private/admin/rate/history")
    public ApiResponse<PaginatedData> getRateHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(true, "Marketplace rate history retrieved", rateService.getRateHistory(page, size));
    }
}