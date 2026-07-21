package com.atlas.cart.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.atlas.cart.client.ExchangeRateService;
import com.atlas.cart.config.CartProperties;
import com.atlas.cart.dto.CartItemUpsertRequest;
import com.atlas.cart.dto.CartResponse;
import com.atlas.cart.dto.MoneyDto;
import com.atlas.cart.entity.*;
import com.atlas.cart.exception.*;
import com.atlas.cart.repository.CartRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");
    private static final String CURRENCY = "USD";
    private static final LocalDate CHECK_IN = LocalDate.of(2026, 2, 1);
    private static final LocalDate CHECK_OUT = LocalDate.of(2026, 2, 4); // 3 nights

    @Mock
    CartRepository cartRepository;

    @Mock
    ExchangeRateService exchangeRateService;

    private CartServiceImpl service;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneId.of("UTC"));
        CartProperties cartProperties = new CartProperties();
        cartProperties.setTtl(Duration.ofMinutes(30));
        service = new CartServiceImpl(cartRepository, cartProperties, fixedClock, exchangeRateService);
    }

    // -- Helpers ---------------------------------------------------------------

    private Cart activeCart() {
        return new Cart(UUID.randomUUID(), USER_ID, NOW.plus(Duration.ofMinutes(30)));
    }

    private Cart expiredCart() {
        return new Cart(UUID.randomUUID(), USER_ID, NOW.minus(Duration.ofMinutes(1)));
    }

    private Cart convertedCart() {
        Cart cart = activeCart();
        cart.convert();
        return cart;
    }

    private CartItemUpsertRequest flightRequest() {
        return new CartItemUpsertRequest(
                UUID.randomUUID(), new MoneyDto(new BigDecimal("250.00"), CURRENCY), 2, null, null);
    }

    private CartItemUpsertRequest hotelRequest() {
        return new CartItemUpsertRequest(
                UUID.randomUUID(), new MoneyDto(new BigDecimal("150.00"), CURRENCY), 3, CHECK_IN, CHECK_OUT);
    }

    private void addFlightItem(Cart cart) {
        CartItemUpsertRequest req = flightRequest();
        cart.addItem(new FlightCartItem(
                UUID.randomUUID(),
                req.resourceId(),
                new Money(req.unitPrice().amount(), req.unitPrice().currency()),
                req.quantity()));
    }

    private void addHotelItem(Cart cart) {
        CartItemUpsertRequest req = hotelRequest();
        cart.addItem(new HotelCartItem(
                UUID.randomUUID(),
                req.resourceId(),
                new Money(req.unitPrice().amount(), req.unitPrice().currency()),
                req.quantity(),
                CHECK_IN,
                CHECK_OUT));
    }

    // -- createOrGetCart --------------------------------------------------------

    @Nested
    class CreateOrGetCart {

        @Test
        void createsNewCartWhenNoneExists() {
            when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.empty());
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            CartResponse response = service.createOrGetCart(USER_ID);

            assertThat(response).isNotNull();
            assertThat(response.status()).isEqualTo(CartStatus.ACTIVE);
            assertThat(response.items()).isEmpty();

            ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
            verify(cartRepository).save(captor.capture());
            Cart saved = captor.getValue();
            assertThat(saved.getUserId()).isEqualTo(USER_ID);
            assertThat(saved.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        }

        @Test
        void returnsExistingActiveCart() {
            Cart existing = activeCart();
            when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(existing));

            CartResponse response = service.createOrGetCart(USER_ID);

            assertThat(response.id()).isEqualTo(existing.getId());
            verify(cartRepository, never()).save(any());
        }

        @Test
        void expiresStaleCartAndCreatesNew() {
            Cart stale = expiredCart();
            when(cartRepository.findByUserIdAndStatus(USER_ID, CartStatus.ACTIVE))
                    .thenReturn(Optional.of(stale));
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));

            CartResponse response = service.createOrGetCart(USER_ID);

            assertThat(stale.getStatus()).isEqualTo(CartStatus.EXPIRED);
            assertThat(response.id()).isNotEqualTo(stale.getId());
            verify(cartRepository).save(any(Cart.class));
        }
    }

    // -- getCart ----------------------------------------------------------------

    @Nested
    class GetCart {

        @Test
        void returnsCartForOwner() {
            Cart cart = activeCart();
            when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

            CartResponse response = service.getCart(cart.getId(), USER_ID);

            assertThat(response.id()).isEqualTo(cart.getId());
        }

        @Test
        void throwsNotFoundForUnknownCart() {
            UUID unknownId = UUID.randomUUID();
            when(cartRepository.findById(unknownId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCart(unknownId, USER_ID)).isInstanceOf(CartNotFoundException.class);
        }

        @Test
        void throwsForbiddenForWrongUser() {
            Cart cart = activeCart();
            when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

            assertThatThrownBy(() -> service.getCart(cart.getId(), OTHER_USER))
                    .isInstanceOf(CartNotOwnedException.class);
        }

        @Test
        void throwsGoneForExpiredCart() {
            Cart cart = expiredCart();
            when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

            assertThatThrownBy(() -> service.getCart(cart.getId(), USER_ID)).isInstanceOf(CartExpiredException.class);
            assertThat(cart.getStatus()).isEqualTo(CartStatus.EXPIRED);
        }
    }

    // -- upsertItem ------------------------------------------------------------

    @Nested
    class UpsertItem {

        @Test
        void addsFlight() {
            Cart cart = activeCart();
            when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

            CartItemUpsertRequest request = flightRequest();
            CartResponse response = service.upsertItem(cart.getId(), USER_ID, CartItemType.FLIGHT, request);

            assertThat(response.items()).hasSize(1);
            assertThat(response.items().getFirst().type()).isEqualTo(CartItemType.FLIGHT);
            assertThat(response.items().getFirst().resourceId()).isEqualTo(request.resourceId());
        }

        @Test
        void replacesExistingFlightWithNew() {
            Cart cart = activeCart();
            addFlightItem(cart);
            assertThat(cart.getCartItems()).hasSize(1);
            when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

            CartItemUpsertRequest newRequest = flightRequest();
            CartResponse response = service.upsertItem(cart.getId(), USER_ID, CartItemType.FLIGHT, newRequest);

            assertThat(response.items()).hasSize(1);
            assertThat(response.items().getFirst().resourceId()).isEqualTo(newRequest.resourceId());
        }

        @Test
        void rejectsCurrencyMismatch() {
            Cart cart = activeCart();
            when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

            CartItemUpsertRequest request = new CartItemUpsertRequest(
                    UUID.randomUUID(), new MoneyDto(new BigDecimal("100.00"), "EUR"), 1, null, null);

            assertThatThrownBy(() -> service.upsertItem(cart.getId(), USER_ID, CartItemType.FLIGHT, request))
                    .isInstanceOf(CurrencyMismatchException.class);
        }

        @Test
        void addsHotelWithStayDates_andEchoesThem() {
            Cart cart = activeCart();
            when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

            CartItemUpsertRequest request = hotelRequest();
            CartResponse response = service.upsertItem(cart.getId(), USER_ID, CartItemType.HOTEL, request);

            assertThat(response.items()).hasSize(1);
            assertThat(response.items().getFirst().type()).isEqualTo(CartItemType.HOTEL);
            assertThat(response.items().getFirst().checkIn()).isEqualTo(CHECK_IN);
            assertThat(response.items().getFirst().checkOut()).isEqualTo(CHECK_OUT);
        }

        @Test
        void rejectsHotelMissingDates() {
            Cart cart = activeCart();
            when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

            CartItemUpsertRequest request = new CartItemUpsertRequest(
                    UUID.randomUUID(), new MoneyDto(new BigDecimal("150.00"), CURRENCY), 1, null, null);

            assertThatThrownBy(() -> service.upsertItem(cart.getId(), USER_ID, CartItemType.HOTEL, request))
                    .isInstanceOf(CartValidationException.class);
        }

        @Test
        void rejectsHotelStayExceedingMaxStay() {
            Cart cart = activeCart();
            when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

            // 40 nights > default max of 30.
            CartItemUpsertRequest request = new CartItemUpsertRequest(
                    UUID.randomUUID(),
                    new MoneyDto(new BigDecimal("150.00"), CURRENCY),
                    1,
                    CHECK_IN,
                    CHECK_IN.plusDays(40));

            assertThatThrownBy(() -> service.upsertItem(cart.getId(), USER_ID, CartItemType.HOTEL, request))
                    .isInstanceOf(CartValidationException.class);
        }

        @Test
        void throwsNotActiveForConvertedCart() {
            Cart cart = convertedCart();
            when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

            assertThatThrownBy(() -> service.upsertItem(cart.getId(), USER_ID, CartItemType.FLIGHT, flightRequest()))
                    .isInstanceOf(CartNotActiveException.class);
        }
    }

    // -- removeItem ------------------------------------------------------------

    @Nested
    class RemoveItem {

        @Test
        void removesExistingItem() {
            Cart cart = activeCart();
            addFlightItem(cart);
            UUID itemId = cart.getCartItems().getFirst().getId();
            when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

            CartResponse response = service.removeItem(cart.getId(), USER_ID, itemId);

            assertThat(response.items()).isEmpty();
        }

        @Test
        void throwsNotFoundForUnknownItem() {
            Cart cart = activeCart();
            when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

            UUID unknownItemId = UUID.randomUUID();
            assertThatThrownBy(() -> service.removeItem(cart.getId(), USER_ID, unknownItemId))
                    .isInstanceOf(CartItemNotFoundException.class);
        }
    }

    // -- clearItems ------------------------------------------------------------

    @Nested
    class ClearItems {

        @Test
        void clearsAllItems() {
            Cart cart = activeCart();
            addFlightItem(cart);
            addHotelItem(cart);
            assertThat(cart.getCartItems()).hasSize(2);
            when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

            CartResponse response = service.clearItems(cart.getId(), USER_ID);

            assertThat(response.items()).isEmpty();
        }
    }

    // -- convertCart ------------------------------------------------------------

    @Nested
    class ConvertCart {

        @Test
        void convertsActiveCart() {
            Cart cart = activeCart();
            when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

            CartResponse response = service.convertCart(cart.getId(), USER_ID);

            assertThat(response.status()).isEqualTo(CartStatus.CONVERTED);
            assertThat(cart.getStatus()).isEqualTo(CartStatus.CONVERTED);
        }

        @Test
        void idempotentOnAlreadyConverted() {
            Cart cart = convertedCart();
            when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

            CartResponse response = service.convertCart(cart.getId(), USER_ID);

            assertThat(response.status()).isEqualTo(CartStatus.CONVERTED);
        }

        @Test
        void throwsGoneOnExpiredCart() {
            Cart cart = expiredCart();
            when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

            assertThatThrownBy(() -> service.convertCart(cart.getId(), USER_ID))
                    .isInstanceOf(CartExpiredException.class);
        }
    }

    // -- Total computation -----------------------------------------------------

    @Nested
    class TotalComputation {

        @Test
        void computesCorrectTotalForMultipleItems() {
            Cart cart = activeCart();
            // Flight: 250.00 * 2 = 500.00
            addFlightItem(cart);
            // Hotel: 150.00 * 3 * 3 = 1350.00
            addHotelItem(cart);
            when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

            CartResponse response = service.getCart(cart.getId(), USER_ID);

            // Total = 500.00 + 1350.00 = 1850.00
            assertThat(response.totalInUSD().amount()).isEqualByComparingTo(new BigDecimal("1850.00"));
            assertThat(response.totalInUSD().currency()).isEqualTo(CURRENCY);
        }

        @Test
        void totalIsZeroForEmptyCart() {
            Cart cart = activeCart();
            when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

            CartResponse response = service.getCart(cart.getId(), USER_ID);

            assertThat(response.totalInUSD().amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        void lineTotalIsCorrectPerItem() {
            Cart cart = activeCart();
            addFlightItem(cart); // 250.00 * 2 = 500.00
            when(cartRepository.findById(cart.getId())).thenReturn(Optional.of(cart));

            CartResponse response = service.getCart(cart.getId(), USER_ID);

            assertThat(response.items().getFirst().priceInUSD().lineTotal())
                    .isEqualByComparingTo(new BigDecimal("500.00"));
        }
    }
}
