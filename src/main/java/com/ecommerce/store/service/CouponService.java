package com.ecommerce.store.service;

import com.ecommerce.store.dto.request.CouponCreateRequest;
import com.ecommerce.store.model.Coupon;
import com.ecommerce.store.repository.CouponRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CouponService {

    private final CouponRepository coupons;

    public CouponService(CouponRepository coupons) {
        this.coupons = coupons;
    }

    @Transactional
    public Coupon create(CouponCreateRequest request) {
        Coupon coupon = new Coupon();
        coupon.setCode(request.code().strip().toUpperCase());   // codes are stored uppercase
        coupon.setDiscountType(request.discountType());
        coupon.setAmount(request.amount());
        coupon.setExpiresAt(request.expiresAt());
        coupon.setActive(request.active() == null || request.active());
        return coupons.save(coupon);
    }
}
