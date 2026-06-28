package com.atlas.cart.service;

import com.atlas.cart.client.ExchangeRateClient;
import com.atlas.cart.client.dto.ExchangeRateDto;
import com.atlas.cart.config.CartProperties;
import com.atlas.cart.dto.*;
import com.atlas.cart.dto.CartItemResponse.Price;
import com.atlas.cart.entity.*;
import com.atlas.cart.exception.*;
import com.atlas.cart.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

  private static final int SCALE = 2;
  private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
  private static final String CURRENCY_USD = "USD";

  private final CartRepository cartRepository;
  private final CartProperties cartProperties;
  private final Clock clock;
  private final ExchangeRateClient exchangeRateClient;

  @Override
  @Transactional
  public CartResponse createOrGetCart(UUID userId) {
    Optional<Cart> existing = cartRepository.findByUserIdAndStatus(userId, CartStatus.ACTIVE);
    if (existing.isPresent()) {
      Cart cart = existing.get();
      if (cart.isExpired(Instant.now(clock))) {
        cart.expire();
        // Fall through to create a new one
      } else {
        return toResponse(cart);
      }
    }

    Cart cart = new Cart(
        UUID.randomUUID(),
        userId,
        Instant.now(clock).plus(cartProperties.getTtl())
    );
    cartRepository.save(cart);

    log.info("Cart created: cartId={}, userId={}", cart.getId(), userId);
    return toResponse(cart);
  }

  @Override
  @Transactional(readOnly = true)
  public CartResponse getCart(UUID cartId, UUID userId) {
    Cart cart = findAndAuthorize(cartId, userId);
    checkNotExpired(cart);
    return toResponse(cart);
  }

  @Override
  @Transactional
  public CartResponse upsertItem(UUID cartId, UUID userId, CartItemType type,
      CartItemUpsertRequest request) {
    Cart cart = findAndAuthorize(cartId, userId);
    checkActive(cart);

    cart.removeItemsByType(type);

    CartItem item = new CartItem(
        UUID.randomUUID(),
        type,
        request.resourceId(),
        new Money(request.unitPrice().amount(), request.unitPrice().currency()),
        request.quantity()
    );

    cart.addItem(item);

    log.info("Cart item upserted: cartId={}, type={}, resourceId={}", cartId, type,
        request.resourceId());
    return toResponse(cart);
  }

  @Override
  @Transactional
  public CartResponse removeItem(UUID cartId, UUID userId, UUID itemId) {
    Cart cart = findAndAuthorize(cartId, userId);
    checkActive(cart);

    if (!cart.removeItemById(itemId)) {
      throw new CartItemNotFoundException(itemId);
    }

    log.info("Cart item removed: cartId={}, itemId={}", cartId, itemId);
    return toResponse(cart);
  }

  @Override
  @Transactional
  public CartResponse clearItems(UUID cartId, UUID userId) {
    Cart cart = findAndAuthorize(cartId, userId);
    checkActive(cart);
    cart.clearItems();

    log.info("Cart cleared: cartId={}", cartId);
    return toResponse(cart);
  }

  @Override
  @Transactional
  public CartResponse convertCart(UUID cartId, UUID userId) {
    Cart cart = findAndAuthorize(cartId, userId);

    if (cart.getStatus() == CartStatus.CONVERTED) {
      return toResponse(cart);
    }
    checkNotExpired(cart);

    cart.convert();
    log.info("Cart converted: cartId={}", cartId);
    return toResponse(cart);
  }

  // -- Helpers ---------------------------------------------------------------

  private Cart findAndAuthorize(UUID cartId, UUID userId) {
    Cart cart = cartRepository.findById(cartId)
        .orElseThrow(() -> new CartNotFoundException(cartId));
    if (!cart.getUserId().equals(userId)) {
      throw new CartNotOwnedException(cartId);
    }
    return cart;
  }

  private void checkNotExpired(Cart cart) {
    if (cart.isExpired(Instant.now(clock))) {
      cart.expire();
      throw new CartExpiredException(cart.getId());
    }
  }

  private void checkActive(Cart cart) {
    checkNotExpired(cart);
    if (cart.getStatus() != CartStatus.ACTIVE) {
      throw new CartNotActiveException(cart.getId());
    }
  }

  private CartResponse toResponse(Cart cart) {
    List<CartItemResponse> itemResponses = cart.getCartItems().stream()
        .map(this::toItemResponse)
        .toList();

    BigDecimal totalAmountInUSD =
        itemResponses.stream()
            .map(cartItem -> cartItem.priceInUSD().lineTotal())
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new CartResponse(
        cart.getId(),
        cart.getStatus(),
        itemResponses,
        new MoneyDto(totalAmountInUSD, CURRENCY_USD),
        cart.getCreatedAt(),
        cart.getUpdatedAt(),
        cart.getExpiresAt()
    );
  }

  private CartItemResponse toItemResponse(CartItem item) {
    BigDecimal unitPriceInUSD;

    if (item.getUnitPrice().getCurrency().equals(CURRENCY_USD)) {
      unitPriceInUSD = item.getUnitPrice().getAmount();

    } else {
      BigDecimal rateToUSD =
          getRateConversionToUSD(item.getUnitPrice().getCurrency(), item.getId());

      unitPriceInUSD = item.getUnitPrice().getAmount().divide(rateToUSD, RoundingMode.HALF_EVEN);
    }

    BigDecimal lineTotalInUSD = unitPriceInUSD
        .multiply(BigDecimal.valueOf(item.getQuantity()))
        .setScale(SCALE, ROUNDING);

    BigDecimal lineTotal = item.getUnitPrice().getAmount()
        .multiply(BigDecimal.valueOf(item.getQuantity()))
        .setScale(SCALE, ROUNDING);

    return CartItemResponse.builder()
        .id(item.getId())
        .type(item.getType())
        .resourceId(item.getResourceId())
        .quantity(item.getQuantity())
        .addedAt(item.getAddedAt())
        .price(
            Price.builder()
                .unitPrice(item.getUnitPrice().getAmount())
                .lineTotal(lineTotal)
                .currency(item.getUnitPrice().getCurrency())
                .build()
        )
        .priceInUSD(
            Price.builder()
            .unitPrice(unitPriceInUSD)
            .lineTotal(lineTotalInUSD)
            .currency(CURRENCY_USD)
            .build())
        .build();
  }

  private BigDecimal getRateConversionToUSD(String currency, UUID cartItemId) {
    if (currency.equals(CURRENCY_USD)) return BigDecimal.ONE;

    List<ExchangeRateDto> exchangeRates = exchangeRateClient.getUSDExchangeRates();
    return exchangeRates.stream()
        .filter(exchange ->
            exchange.quote().equals(currency)
        ).map(ExchangeRateDto::rate)
        .findFirst()
        .orElseThrow(() -> new CurrencyMismatchException(cartItemId));
  }
}
