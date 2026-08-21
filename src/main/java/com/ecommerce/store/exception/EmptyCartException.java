package com.ecommerce.store.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Raised when checkout is attempted with no items in the cart. */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class EmptyCartException extends RuntimeException {

    public EmptyCartException() {
        super("Cannot check out an empty cart");
    }
}
