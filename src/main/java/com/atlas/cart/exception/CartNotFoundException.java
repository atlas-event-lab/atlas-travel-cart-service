package com.atlas.cart.exception;

import java.util.UUID;

public class CartNotFoundException extends RuntimeException {
    public CartNotFoundException(UUID cartId) {
        super("Cart not found: " + cartId);
    }
}
