package com.atlas.cart.exception;

/**
 * Raised on invalid cart input that field-level Bean Validation can't express (cross-field rules such
 * as hotel stay dates, ADR-0011). Mapped to 400 RFC7807.
 */
public class CartValidationException extends RuntimeException {

    public CartValidationException(String message) {
        super(message);
    }
}
