package com.atlas.cart.dto;

import com.atlas.cart.entity.CartStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CartResponse(
        UUID id,
        CartStatus status,
        List<CartItemResponse> items,
        MoneyDto totalInUSD,
        Instant createdAt,
        Instant updatedAt,
        Instant expiresAt) {}
