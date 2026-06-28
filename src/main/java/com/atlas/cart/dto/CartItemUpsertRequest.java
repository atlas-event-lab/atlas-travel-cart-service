package com.atlas.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CartItemUpsertRequest(
    @NotNull UUID resourceId,
    @NotNull MoneyDto unitPrice,
    @NotNull @Min(1) Integer quantity
) {}
