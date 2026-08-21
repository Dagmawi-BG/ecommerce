package com.ecommerce.store.dto.request;

import com.ecommerce.store.model.CouponType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponCreateRequest(
        @NotBlank String code,
        @NotNull CouponType discountType,
        @NotNull @DecimalMin("0.00") BigDecimal amount,
        Instant expiresAt,
        Boolean active) {
}
