package com.ecommerce.store.controller.api;

import com.ecommerce.store.dto.request.CouponCreateRequest;
import com.ecommerce.store.model.Coupon;
import com.ecommerce.store.service.CouponService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coupons")
public class CouponApiController {

    private final CouponService couponService;

    public CouponApiController(CouponService couponService) {
        this.couponService = couponService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CouponView create(@Valid @RequestBody CouponCreateRequest request) {
        Coupon c = couponService.create(request);
        return new CouponView(c.getId(), c.getCode(), c.getDiscountType().name(),
                c.getAmount(), c.isActive());
    }

    public record CouponView(Long id, String code, String discountType,
                             java.math.BigDecimal amount, boolean active) {
    }
}
