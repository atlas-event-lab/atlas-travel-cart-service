package com.atlas.cart.exception;

import java.util.UUID;

public class CartNotOwnedException extends RuntimeException {
    public CartNotOwnedException(UUID cartId) {
        super("Access denied to cart: " + cartId);
    }
}
