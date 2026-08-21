package com.ecommerce.store.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ApplyCouponRequest(@NotBlank String code) {
}
