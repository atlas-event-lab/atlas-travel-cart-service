package com.atlas.cart.exception;

import java.util.UUID;

public class CurrencyMismatchException  extends RuntimeException {
  public CurrencyMismatchException(UUID itemId) {
    super("Currency mismatch on itemId: " + itemId);
  }
}
