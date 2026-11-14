package com.echaris.api.marketplace;

import com.echaris.api.marketplace.request.MarketplaceRateRequest;
import com.echaris.api.utils.ApiResponse;
import com.echaris.api.utils.PaginatedData;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/marketplace/rate")
@RequiredArgsConstructor
public class MarketplaceRateController {

    private final MarketplaceRateService rateService;

    @PutMapping("/private")
    public ApiResponse<MarketplaceRate> updateRate(
            @Valid @RequestBody MarketplaceRateRequest request
    ) {
        return new ApiResponse<>(
                true,
                "Marketplace rate updated",
                rateService.updateRate(request)
        );
    }


    @GetMapping("/private")
    public ApiResponse<MarketplaceRate> getRate() {
        return new ApiResponse<>(
                true,
                "Marketplace rate retrieved",
                rateService.getRate()
        );
    }


}