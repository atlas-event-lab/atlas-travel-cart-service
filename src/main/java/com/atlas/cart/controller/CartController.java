package com.atlas.cart.controller;

import com.atlas.cart.dto.CartItemUpsertRequest;
import com.atlas.cart.dto.CartResponse;
import com.atlas.cart.entity.CartItemType;
import com.atlas.cart.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for the Travel Cart API (travel-cart.yaml).
 * Contains no business logic (API-003); delegates to CartService.
 * UserId is extracted from the JWT (SEC-004).
 */
@RestController
@RequestMapping("/carts")
@RequiredArgsConstructor
public class CartController {

  private final CartService cartService;

    @PostMapping
    public ResponseEntity<CartResponse> createCart(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = extractUserId(jwt);
        CartResponse response = cartService.createOrGetCart(userId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{cartId}")
    public ResponseEntity<CartResponse> getCart(
            @PathVariable UUID cartId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(cartService.getCart(cartId, extractUserId(jwt)));
    }

    @PutMapping("/{cartId}/flight")
    public ResponseEntity<CartResponse> putFlight(
            @PathVariable UUID cartId,
            @RequestBody @Valid CartItemUpsertRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(cartService.upsertItem(cartId, extractUserId(jwt), CartItemType.FLIGHT, request));
    }

    @PutMapping("/{cartId}/hotel")
    public ResponseEntity<CartResponse> putHotel(
            @PathVariable UUID cartId,
            @RequestBody @Valid CartItemUpsertRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(cartService.upsertItem(cartId, extractUserId(jwt), CartItemType.HOTEL, request));
    }

    @DeleteMapping("/{cartId}/items/{itemId}")
    public ResponseEntity<CartResponse> removeItem(
            @PathVariable UUID cartId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(cartService.removeItem(cartId, extractUserId(jwt), itemId));
    }

    @DeleteMapping("/{cartId}/items")
    public ResponseEntity<CartResponse> clearItems(
            @PathVariable UUID cartId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(cartService.clearItems(cartId, extractUserId(jwt)));
    }

    @PostMapping("/{cartId}/conversion")
    public ResponseEntity<CartResponse> convertCart(
            @PathVariable UUID cartId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(cartService.convertCart(cartId, extractUserId(jwt)));
    }

    private UUID extractUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
