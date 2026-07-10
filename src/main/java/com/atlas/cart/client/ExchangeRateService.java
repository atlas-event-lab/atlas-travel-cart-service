package com.atlas.cart.client;

import com.atlas.cart.client.dto.ExchangeRateDto;
import com.atlas.cart.config.CacheConfig;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Caches the USD exchange-rate table so cart repricing does not call the Frankfurter API on every
 * change. The table is currency-independent, so it is cached under a single key with a 15-minute
 * TTL, shared with booking-service (see {@link CacheConfig}).
 *
 * <p>Dedicated bean (not a method on {@code CartServiceImpl}) so the {@code @Cacheable} proxy is
 * actually hit — a self-invocation inside {@code CartServiceImpl} would bypass it. Feign failures
 * propagate (not cached), so the caller's error handling still applies.
 */
@Service
@RequiredArgsConstructor
public class ExchangeRateService {

    private final ExchangeRateClient exchangeRateClient;

    @Cacheable(cacheNames = CacheConfig.USD_EXCHANGE_RATES, key = "'ALL'")
    public List<ExchangeRateDto> getUSDExchangeRates() {
        return exchangeRateClient.getUSDExchangeRates();
    }
}
