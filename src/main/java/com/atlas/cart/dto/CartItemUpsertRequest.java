package com.atlas.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Upsert request for a cart flight/hotel selection (travel-cart.yaml). {@code checkIn} / {@code checkOut}
 * are required for HOTEL items (the stay range, ADR-0011) and omitted for FLIGHT; the service validates
 * the range.
 */
public record CartItemUpsertRequest(
        @NotNull UUID resourceId,
        @NotNull MoneyDto unitPrice,
        @NotNull @Min(1) Integer quantity,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut) {}
