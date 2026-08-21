package com.ecommerce.store.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Raised when a requested quantity exceeds available product stock.
 * Used by cart add/update and (critically) by checkout stock reduction.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String sku, int available, int requested) {
        super("Insufficient stock for " + sku + ": requested " + requested + ", available " + available);
    }
}
