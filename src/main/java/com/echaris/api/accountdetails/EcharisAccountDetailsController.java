package com.echaris.api.accountdetails;

import com.echaris.api.accountdetails.request.EcharisAccountDetailsRequest;
import com.echaris.api.accountdetails.response.EcharisAccountDetailsResponse;
import com.echaris.api.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/account-details")
public class EcharisAccountDetailsController {

    private final EcharisAccountDetailsService accountDetailsService;

    @GetMapping("/private/admin")
    public ApiResponse<EcharisAccountDetailsResponse> getCurrent() {
        return new ApiResponse<>(true, "Account details retrieved successfully", accountDetailsService.getCurrent());
    }

    @PutMapping("/private/admin")
    public ApiResponse<EcharisAccountDetailsResponse> createOrUpdate(
            @Valid @RequestBody EcharisAccountDetailsRequest request
    ) {
        return new ApiResponse<>(true, "Account details saved successfully", accountDetailsService.createOrUpdate(request));
    }

    @GetMapping("/private/admin/history")
    public ApiResponse<List<EcharisAccountDetailsHistory>> getHistory() {
        return new ApiResponse<>(true, "Account history retrieved successfully", accountDetailsService.getHistory());
    }
}