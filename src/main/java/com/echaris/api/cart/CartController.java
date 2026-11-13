package com.echaris.api.cart;

import com.echaris.api.cart.request.CartItemQuantityRequest;
import com.echaris.api.cart.request.CartItemRemoveRequest;
import com.echaris.api.cart.response.CartResponse;
import com.echaris.api.utils.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/cart")
public class CartController {

    private final CartService cartService;

    @GetMapping("/private")
    public ApiResponse<CartResponse> getCart(@RequestAttribute("userId") String userId) {
        return new ApiResponse<>(true, "Cart retrieved", cartService.getCart(userId));
    }

    @PostMapping("/private/add")
    public ApiResponse<CartResponse> addItem(
            @RequestAttribute("userId") String userId,
            @Valid @RequestBody CartItemQuantityRequest request
    ) {
        return new ApiResponse<>(true, "Cart item saved", cartService.addItem(userId, request));
    }

    @PostMapping("/private/remove")
    public ApiResponse<CartResponse> removeItem(
            @RequestAttribute("userId") String userId,
            @Valid @RequestBody CartItemRemoveRequest request
    ) {
        return new ApiResponse<>(true, "Cart item removed", cartService.removeItem(userId, request));
    }

    @PostMapping("/private/clear")
    public ApiResponse<CartResponse> clearCart(@RequestAttribute("userId") String userId) {
        return new ApiResponse<>(true, "Cart cleared", cartService.clearCart(userId));
    }
}