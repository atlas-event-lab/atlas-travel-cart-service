package com.atlas.cart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.Entity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A hotel selection in a Cart (ADR-0011). Carries {@code checkIn} / {@code checkOut} as first-class
 * fields (the stay occupies {@code [checkIn, checkOut)}) so the dates survive the Search → Cart →
 * Booking hand-off. Persisted in the shared {@code cart_items} table with discriminator {@code HOTEL}.
 */
@Entity
@DiscriminatorValue("HOTEL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HotelCartItem extends CartItem {

    @Column(name = "check_in")
    private LocalDate checkIn;

    @Column(name = "check_out")
    private LocalDate checkOut;

    public HotelCartItem(UUID id, UUID resourceId, Money unitPrice, int quantity,
                         LocalDate checkIn, LocalDate checkOut) {
        super(id, resourceId, unitPrice, quantity);
        this.checkIn = checkIn;
        this.checkOut = checkOut;
    }

    @Override
    public CartItemType type() {
        return CartItemType.HOTEL;
    }
}
