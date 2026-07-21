package com.atlas.cart.service;

import com.atlas.cart.client.ExchangeRateService;
import com.atlas.cart.client.dto.ExchangeRateDto;
import com.atlas.cart.config.CartProperties;
import com.atlas.cart.dto.CartItemResponse;
import com.atlas.cart.dto.CartItemResponse.Price;
import com.atlas.cart.dto.CartItemUpsertRequest;
import com.atlas.cart.dto.CartResponse;
import com.atlas.cart.dto.MoneyDto;
import com.atlas.cart.entity.Cart;
import com.atlas.cart.entity.CartItem;
import com.atlas.cart.entity.CartItemType;
import com.atlas.cart.entity.CartStatus;
import com.atlas.cart.entity.FlightCartItem;
import com.atlas.cart.entity.HotelCartItem;
import com.atlas.cart.entity.Money;
import com.atlas.cart.exception.CartExpiredException;
import com.atlas.cart.exception.CartItemNotFoundException;
import com.atlas.cart.exception.CartNotActiveException;
import com.atlas.cart.exception.CartNotFoundException;
import com.atlas.cart.exception.CartNotOwnedException;
import com.atlas.cart.exception.CartValidationException;
import com.atlas.cart.exception.CurrencyMismatchException;
import com.atlas.cart.repository.CartRepository;
import feign.FeignException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private static final int SCALE = 2;
    private static final int CONVERSION_SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_EVEN;
    private static final String CURRENCY_USD = "USD";

    private final CartRepository cartRepository;
    private final CartProperties cartProperties;
    private final Clock clock;
    private final ExchangeRateService exchangeRateService;

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

        Cart cart = new Cart(UUID.randomUUID(), userId, Instant.now(clock).plus(cartProperties.getTtl()));
        cartRepository.save(cart);

        log.info("Cart created: cartId={}, userId={}", cart.getId(), userId);
        return toResponse(cart);
    }

    @Override
    @Transactional(readOnly = true)
    public CartResponse getCart(UUID cartId, UUID userId) {
        log.info("Get cart Id={} for userId={}", cartId, userId);
        Cart cart = findAndAuthorize(cartId, userId);
        checkNotExpired(cart);
        return toResponse(cart);
    }

    @Override
    @Transactional
    public CartResponse upsertItem(UUID cartId, UUID userId, CartItemType type, CartItemUpsertRequest request) {
        Cart cart = findAndAuthorize(cartId, userId);
        checkActive(cart);

        cart.removeItemsByType(type);
        cart.addItem(buildItem(type, request));

        log.info("Cart item upserted: cartId={}, type={}, resourceId={}", cartId, type, request.resourceId());
        return toResponse(cart);
    }

    /** Builds the polymorphic cart item; hotel items validate and carry the stay range (ADR-0011). */
    private CartItem buildItem(CartItemType type, CartItemUpsertRequest request) {
        Money unitPrice =
                new Money(request.unitPrice().amount(), request.unitPrice().currency());
        if (type == CartItemType.HOTEL) {
            validateHotelStay(request);
            return new HotelCartItem(
                    UUID.randomUUID(),
                    request.resourceId(),
                    unitPrice,
                    request.quantity(),
                    request.checkIn(),
                    request.checkOut());
        }
        return new FlightCartItem(UUID.randomUUID(), request.resourceId(), unitPrice, request.quantity());
    }

    /** Hotel stay-date rules (ADR-0011): dates present, checkOut > checkIn, checkIn ≥ today, nights ≤ maxStay. */
    private void validateHotelStay(CartItemUpsertRequest request) {
        LocalDate checkIn = request.checkIn();
        LocalDate checkOut = request.checkOut();
        if (checkIn == null || checkOut == null) {
            throw new CartValidationException("checkIn and checkOut are required for HOTEL items");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new CartValidationException(
                    "checkOut must be after checkIn (checkIn=" + checkIn + ", checkOut=" + checkOut + ")");
        }
        if (checkIn.isBefore(LocalDate.now(clock))) {
            throw new CartValidationException("checkIn must be today or in the future (checkIn=" + checkIn + ")");
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights > cartProperties.getMaxStayNights()) {
            throw new CartValidationException(
                    "stay length (" + nights + " nights) exceeds the maximum of " + cartProperties.getMaxStayNights());
        }
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
        Cart cart = cartRepository.findById(cartId).orElseThrow(() -> new CartNotFoundException(cartId));
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
        List<CartItemResponse> itemResponses =
                cart.getCartItems().stream().map(this::toItemResponse).toList();

        BigDecimal totalAmountInUSD = itemResponses.stream()
                .map(cartItem -> cartItem.priceInUSD().lineTotal())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CartResponse(
                cart.getId(),
                cart.getStatus(),
                itemResponses,
                new MoneyDto(totalAmountInUSD, CURRENCY_USD),
                cart.getCreatedAt(),
                cart.getUpdatedAt(),
                cart.getExpiresAt());
    }

    private CartItemResponse toItemResponse(CartItem item) {
        BigDecimal unitPriceInUSD;

        if (item.getUnitPrice().getCurrency().equals(CURRENCY_USD)) {
            unitPriceInUSD = item.getUnitPrice().getAmount();

        } else {
            BigDecimal rateToUSD = getRateConversionToUSD(item.getUnitPrice().getCurrency(), item.getId());

            unitPriceInUSD = item.getUnitPrice().getAmount().divide(rateToUSD, CONVERSION_SCALE, ROUNDING);
        }

        // A hotel is charged for the whole stay: pricePerNight × nights × rooms (ADR-0010/0011),
        // so the cart total matches what Booking recomputes and re-validates. Flights = unitPrice × qty.
        long units = item.getQuantity();
        if (item instanceof HotelCartItem hotel) {
            units *= ChronoUnit.DAYS.between(hotel.getCheckIn(), hotel.getCheckOut());
        }

        BigDecimal lineTotalInUSD =
                unitPriceInUSD.multiply(BigDecimal.valueOf(units)).setScale(SCALE, ROUNDING);

        BigDecimal lineTotal = item.getUnitPrice()
                .getAmount()
                .multiply(BigDecimal.valueOf(units))
                .setScale(SCALE, ROUNDING);

        LocalDate checkIn = (item instanceof HotelCartItem hotel) ? hotel.getCheckIn() : null;
        LocalDate checkOut = (item instanceof HotelCartItem hotel) ? hotel.getCheckOut() : null;

        return CartItemResponse.builder()
                .id(item.getId())
                .type(item.type())
                .resourceId(item.getResourceId())
                .quantity(item.getQuantity())
                .addedAt(item.getAddedAt())
                .checkIn(checkIn)
                .checkOut(checkOut)
                .price(Price.builder()
                        .unitPrice(item.getUnitPrice().getAmount())
                        .lineTotal(lineTotal)
                        .currency(item.getUnitPrice().getCurrency())
                        .build())
                .priceInUSD(Price.builder()
                        .unitPrice(unitPriceInUSD)
                        .lineTotal(lineTotalInUSD)
                        .currency(CURRENCY_USD)
                        .build())
                .build();
    }

    private BigDecimal getRateConversionToUSD(String currency, UUID cartItemId) {
        if (currency.equals(CURRENCY_USD)) {
            return BigDecimal.ONE;
        }
        List<ExchangeRateDto> exchangeRates;
        try {
            exchangeRates = exchangeRateService.getUSDExchangeRates();
        } catch (FeignException e) {
            log.error("Exchange Rate unavailable for Currency={}", currency, e);
            throw new CurrencyMismatchException(cartItemId);
        }

        return exchangeRates.stream()
                .filter(exchange -> exchange.quote().equals(currency))
                .map(ExchangeRateDto::rate)
                .findFirst()
                .orElseThrow(() -> new CurrencyMismatchException(cartItemId));
    }
}
