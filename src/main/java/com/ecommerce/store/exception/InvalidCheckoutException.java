package com.ecommerce.store.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Raised when a partial-checkout selection is inconsistent with the cart. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidCheckoutException extends RuntimeException {

    public InvalidCheckoutException(String message) {
        super(message);
    }
}
