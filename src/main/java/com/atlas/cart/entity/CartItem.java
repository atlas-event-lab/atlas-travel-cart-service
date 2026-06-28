package com.atlas.cart.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cart_items")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class CartItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private CartItemType type;

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

    public CartItem(UUID id, CartItemType type, UUID resourceId, Money unitPrice, int quantity) {
        this.id = id;
        this.type = type;
        this.resourceId = resourceId;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }
}
