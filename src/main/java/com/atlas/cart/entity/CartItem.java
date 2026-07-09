package com.atlas.cart.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * A line in a Cart (ADR-0011). Abstract base of a JPA {@code SINGLE_TABLE} hierarchy:
 * {@link FlightCartItem} (no dates) and {@link HotelCartItem} (carries the stay range). One physical
 * {@code cart_items} table discriminated by {@code type}; the hotel date columns are nullable.
 */
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING, length = 10)
@Table(name = "cart_items")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public abstract class CartItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Column(name = "resource_id", nullable = false)
    private UUID resourceId;

    @Embedded
    @AttributeOverrides({
        @AttributeOverride(name = "amount", column = @Column(name = "unit_price_amount", nullable = false, precision = 19, scale = 2)),
        @AttributeOverride(name = "currency", column = @Column(name = "unit_price_currency", nullable = false, length = 3))
    })
    private Money unitPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @CreatedDate
    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    protected CartItem(UUID id, UUID resourceId, Money unitPrice, int quantity) {
        this.id = id;
        this.resourceId = resourceId;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    /** The item type; drives the discriminator and response shape. */
    public abstract CartItemType type();
}
