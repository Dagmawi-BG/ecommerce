package com.ecommerce.store.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested domain entity does not exist.
 * Phase 4 will replace the {@code @ResponseStatus} shortcut with an
 * RFC 7807 ProblemDetail via a @ControllerAdvice.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " not found: " + id);
    }
}
