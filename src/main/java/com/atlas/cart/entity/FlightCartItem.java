package com.atlas.cart.entity;

import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * A flight selection in a Cart (ADR-0011). Carries no stay dates. Persisted in the shared
 * {@code cart_items} table with discriminator {@code FLIGHT}.
 */
@Entity
@DiscriminatorValue("FLIGHT")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlightCartItem extends CartItem {

    public FlightCartItem(UUID id, UUID resourceId, Money unitPrice, int quantity) {
        super(id, resourceId, unitPrice, quantity);
    }

    @Override
    public CartItemType type() {
        return CartItemType.FLIGHT;
    }
}
