package com.atlas.cart.exception;

import java.util.UUID;

public class CartNotActiveException extends RuntimeException {
    public CartNotActiveException(UUID cartId) {
        super("Cart is not active: " + cartId);
    }
}
