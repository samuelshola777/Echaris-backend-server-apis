package com.echaris.api.cart;

import com.echaris.api.cart.request.CartItemQuantityRequest;
import com.echaris.api.cart.request.CartItemRemoveRequest;
import com.echaris.api.cart.response.CartItemResponse;
import com.echaris.api.cart.response.CartResponse;
import com.echaris.api.product.Product;
import com.echaris.api.product.ProductService;
import com.echaris.api.utils.exception.InvalidInputException;
import com.echaris.api.utils.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CartService {

    private final MongoTemplate mongoTemplate;
    private final ProductService productService;

    public CartResponse getCart(String userId) {
        Cart cart = findCart(userId);
        return response(cart, cart.getItems(), catalogProducts(cart.getItems()));
    }

    public CartResponse addItem(String userId, CartItemQuantityRequest request) {
        String productId = request.productId().strip();
        int quantity = request.quantity().intValueExact();

        if (quantity < 1) {
            throw new InvalidInputException("Cart item quantity must be positive");
        }

        Cart cart = findCart(userId);
        List<CartItem> items = withQuantity(cart.getItems(), new CartItem(productId, quantity));
        Map<String, Product> products = catalogProducts(items);

        Product product = products.get(productId);
        if (product == null || product.getPrice() == null) {
            throw new ResourceNotFoundException("Product is unavailable");
        }
        if (quantity > product.getStockQuantity()) {
            throw new InvalidInputException("Requested quantity exceeds available stock");
        }

        CartResponse response = response(cart, items, products);
        saveItems(cart, items);
        return response;
    }

    public CartResponse removeItem(String userId, CartItemRemoveRequest request) {
        String productId = request.productId().strip();
        Cart cart = findCart(userId);
        List<CartItem> items = cart.getItems().stream()
                .filter(item -> !item.productId().equals(productId))
                .toList();

        CartResponse response = response(cart, items, catalogProducts(items));
        saveItems(cart, items);
        return response;
    }

    public CartResponse clearCart(String userId) {
        Cart cart = findCart(userId);
        saveItems(cart, List.of());
        return response(cart, List.of(), Map.of());
    }

    private Cart findCart(String userId) {
        Cart cart = mongoTemplate.findById(userId, Cart.class);
        if (cart != null) {
            return cart;
        }

        Cart emptyCart = new Cart();
        // The owner's ID also serves as the primary key, enforcing one cart per customer.
        emptyCart.setId(userId);
        emptyCart.setUserId(userId);
        return emptyCart;
    }

    private List<CartItem> withQuantity(List<CartItem> currentItems, CartItem updatedItem) {
        List<CartItem> items = new ArrayList<>(currentItems);
        var iterator = items.listIterator();

        while (iterator.hasNext()) {
            if (iterator.next().productId().equals(updatedItem.productId())) {
                iterator.set(updatedItem);
                return items;
            }
        }

        items.add(updatedItem);
        return items;
    }

    private void saveItems(Cart cart, List<CartItem> items) {
        if (cart.getItems().equals(items)) {
            return;
        }

        cart.setItems(items);
        cart.setUpdatedAt(LocalDateTime.now());

        if (cart.getVersion() != null) {
            mongoTemplate.save(cart);
            return;
        }

        try {
            mongoTemplate.insert(cart);
        } catch (DuplicateKeyException exception) {
            throw new OptimisticLockingFailureException("The cart changed while saving", exception);
        }
    }

    private Map<String, Product> catalogProducts(List<CartItem> items) {
        List<String> productIds = items.stream().map(CartItem::productId).toList();
        return productService.catalogProducts(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
    }

    private CartResponse response(Cart cart, List<CartItem> items, Map<String, Product> products) {
        List<CartItemResponse> itemResponses = items.stream()
                .map(item -> itemResponse(item, products.get(item.productId())))
                .toList();

        BigDecimal subtotal = itemResponses.stream()
                .filter(CartItemResponse::available)
                .map(CartItemResponse::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(cart.getId(), cart.getUserId(), itemResponses, subtotal);
    }

    private CartItemResponse itemResponse(CartItem item, Product product) {
        if (product == null) {
            return new CartItemResponse(item.productId(), null, null, item.quantity(), null, null, false);
        }

        BigDecimal unitPrice = product.getPrice();
        BigDecimal lineTotal = unitPrice == null ? null : unitPrice.multiply(BigDecimal.valueOf(item.quantity()));
        List<String> imageUrls = product.getImageUrls();
        String imageUrl = imageUrls == null || imageUrls.isEmpty() ? null : imageUrls.get(0);
        boolean available = unitPrice != null && item.quantity() <= product.getStockQuantity();

        return new CartItemResponse(item.productId(), product.getName(), imageUrl, item.quantity(),
                unitPrice, lineTotal, available);
    }
}