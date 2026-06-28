package com.atlas.cart.exception;

import java.util.UUID;

public class CartExpiredException extends RuntimeException {
    public CartExpiredException(UUID cartId) {
        super("Cart expired: " + cartId);
    }
}
