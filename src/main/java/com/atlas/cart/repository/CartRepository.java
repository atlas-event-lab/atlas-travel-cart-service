package com.atlas.cart.repository;

import com.atlas.cart.entity.Cart;
import com.atlas.cart.entity.CartStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CartRepository extends JpaRepository<Cart, UUID> {

    Optional<Cart> findByUserIdAndStatus(UUID userId, CartStatus status);

    @Modifying
    @Query("UPDATE Cart c SET c.status = 'EXPIRED' WHERE c.status = 'ACTIVE' AND c.expiresAt <= :threshold")
    int expireStaleCartsBeforeThreshold(@Param("threshold") Instant threshold);
}
