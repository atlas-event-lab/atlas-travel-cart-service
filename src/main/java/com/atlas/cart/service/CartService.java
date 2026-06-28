package com.atlas.cart.service;

import com.atlas.cart.dto.CartItemUpsertRequest;
import com.atlas.cart.dto.CartResponse;
import com.atlas.cart.entity.CartItemType;

import java.util.UUID;

public interface CartService {

    CartResponse createOrGetCart(UUID userId);

    CartResponse getCart(UUID cartId, UUID userId);

    CartResponse upsertItem(UUID cartId, UUID userId, CartItemType type, CartItemUpsertRequest request);

    CartResponse removeItem(UUID cartId, UUID userId, UUID itemId);

    CartResponse clearItems(UUID cartId, UUID userId);

    CartResponse convertCart(UUID cartId, UUID userId);
}
