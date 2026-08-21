package com.ecommerce.store.controller.api;

import com.ecommerce.store.dto.request.ApplyCouponRequest;
import com.ecommerce.store.dto.request.CartItemRequest;
import com.ecommerce.store.dto.response.CartResponse;
import com.ecommerce.store.service.CartService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cart")
public class CartApiController {

    private final CartService cartService;

    public CartApiController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse view() {
        return cartService.view();
    }

    @PostMapping("/items")
    public CartResponse addItem(@Valid @RequestBody CartItemRequest request) {
        return cartService.addItem(request);
    }

    @PutMapping("/items/{productId}")
    public CartResponse updateItem(@PathVariable Long productId, @RequestParam int quantity) {
        return cartService.updateQuantity(productId, quantity);
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse removeItem(@PathVariable Long productId) {
        return cartService.removeItem(productId);
    }

    @DeleteMapping
    public ResponseEntity<Void> clear() {
        cartService.clear();
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/coupon")
    public CartResponse applyCoupon(@Valid @RequestBody ApplyCouponRequest request) {
        return cartService.applyCoupon(request.code());
    }
}
