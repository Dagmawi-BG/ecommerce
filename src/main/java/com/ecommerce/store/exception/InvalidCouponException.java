package com.ecommerce.store.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Raised when a coupon code is unknown, inactive, or expired. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidCouponException extends RuntimeException {

    public InvalidCouponException(String message) {
        super(message);
    }
}
