package com.echaris.api.address;

import com.echaris.api.address.request.AddressRequest;
import com.echaris.api.address.response.AddressResponse;
import com.echaris.api.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/addresses")
public class AddressController {

    private final AddressService addressService;

    @PostMapping("/private/create-or-update")
    public ApiResponse<AddressResponse> createOrUpdateAddress(
            @Valid @RequestBody AddressRequest request
    ) {
        return new ApiResponse<>(
                true,
                "Address saved successfully",
                addressService.createOrUpdateAddress(request)
        );
    }

    @GetMapping("/private/user/{userId}")
    public ApiResponse<AddressResponse> getAddressByUserId(
            @PathVariable String userId
    ) {
        return new ApiResponse<>(
                true,
                "Address retrieved successfully",
                addressService.getAddressByUserId(userId)
        );
    }

    @GetMapping("/private")
    public ApiResponse<List<AddressResponse>> getAddresses(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return new ApiResponse<>(
                true,
                "Addresses retrieved successfully",
                addressService.getAddresses(page, size)
        );
    }

    @DeleteMapping("/private/user/{userId}")
    public ResponseEntity<Void> deleteAddressByUserId(
            @PathVariable String userId
    ) {
        addressService.deleteAddressByUserId(userId);
        return ResponseEntity.noContent().build();
    }
}