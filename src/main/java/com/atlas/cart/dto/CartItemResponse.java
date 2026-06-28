package com.atlas.cart.dto;

import com.atlas.cart.entity.CartItemType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;

@Builder
public record CartItemResponse(
    UUID id,
    CartItemType type,
    UUID resourceId,
    Instant addedAt,
    Integer quantity,
    Price price,
    Price priceInUSD
) {

  @Builder
  public record Price(
      BigDecimal unitPrice,
      BigDecimal lineTotal,
      String currency
  ) { }
}
