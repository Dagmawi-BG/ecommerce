package com.ecommerce.store.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Shipping details plus an optional partial-checkout {@code selection}.
 * When {@code selection} is null/empty the whole cart is ordered; otherwise only
 * the listed products are ordered (a null quantity means "the whole line").
 */
public record CheckoutRequest(
        @NotBlank @Size(max = 255) String street,
        @NotBlank @Size(max = 100) String city,
        @NotBlank @Size(max = 20) String zip,
        @Valid List<LineSelection> selection) {

    /** Backwards-compatible whole-cart checkout. */
    public CheckoutRequest(String street, String city, String zip) {
        this(street, city, zip, null);
    }

    public boolean hasSelection() {
        return selection != null && !selection.isEmpty();
    }

    public record LineSelection(
            @NotNull Long productId,
            @Min(1) Integer quantity) {   // null quantity => the whole cart line
    }
}
