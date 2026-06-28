package com.atlas.cart.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "carts")
@EntityListeners(AuditingEntityListener.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Cart {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CartStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @OneToMany(mappedBy = "cart", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<CartItem> cartItems = new ArrayList<>();

    public Cart(UUID id, UUID userId, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.status = CartStatus.ACTIVE;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return status == CartStatus.ACTIVE && !now.isBefore(expiresAt);
    }

    public void expire() { this.status = CartStatus.EXPIRED; }

    public void convert() { this.status = CartStatus.CONVERTED; }

    public void addItem(CartItem item) {
        cartItems.add(item);
        item.setCart(this);
    }

    public void removeItemsByType(CartItemType type) {
        cartItems.removeIf(cartItem -> cartItem.getType() == type);
    }

    public boolean removeItemById(UUID itemId) {
        return cartItems.removeIf(cartItem -> cartItem.getId().equals(itemId));
    }

    public void clearItems() {
        cartItems.clear();
    }

    public List<CartItem> getCartItems() {
        return Collections.unmodifiableList(cartItems);
    }
}
