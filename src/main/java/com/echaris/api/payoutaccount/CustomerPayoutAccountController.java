package com.echaris.api.payoutaccount;

import com.echaris.api.payoutaccount.response.CustomerPayoutAccountResponse;
import com.echaris.api.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/customer")
public class CustomerPayoutAccountController {

    private final CustomerPayoutAccountService service;

    @PostMapping("/private/payout-account")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CustomerPayoutAccountResponse> create(
            @Valid @RequestBody CustomerPayoutAccountDetails request
    ) {
        return new ApiResponse<>(true, "Payout account created", service.create(request));
    }

    @GetMapping("/private/payout-account")
    public ApiResponse<CustomerPayoutAccountResponse> get() {
        return new ApiResponse<>(true, "Payout account retrieved", service.get());
    }

    @PutMapping("/private/payout-account")
    public ApiResponse<CustomerPayoutAccountResponse> update(
            @RequestParam("version") long version,
            @Valid @RequestBody CustomerPayoutAccountDetails request
    ) {
        return new ApiResponse<>(true, "Payout account updated", service.update(version, request));
    }

    @DeleteMapping("/private/payout-account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@RequestParam("version") long version) {
        service.delete(version);
    }

    @GetMapping("/private/payout-account/history")
    public ApiResponse<List<CustomerPayoutAccountHistory>> getHistory() {
        return new ApiResponse<>(true, "Payout account history retrieved", service.getHistory());
    }
}