package com.atlas.cart.config;

import com.atlas.cart.client.dto.ExchangeRateDto;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

/**
 * Redis-backed caching for travel-cart-service. Caches the USD exchange-rate table
 * ({@link com.atlas.cart.client.ExchangeRateService}) so repricing does not call the Frankfurter API
 * on every cart change. TTL defaults to 15 minutes
 * ({@code cart.exchange-rate.cache-ttl} / {@code CART_EXCHANGE_RATE_CACHE_TTL}).
 *
 * <p>Values are stored as <b>plain JSON without type metadata</b> and under the same cache name/key
 * ({@code usdExchangeRates::ALL}) as booking-service, so BOTH services share the identical rate
 * table and price consistently — a cart total never disagrees with Booking's recomputation because
 * of a stale/offset FX window. Keep {@link #USD_EXCHANGE_RATES} and the key in sync with booking.
 *
 * <p>Cache failures degrade gracefully: a Redis outage is logged and ignored, and repricing falls
 * back to calling Frankfurter directly.
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    /** Cache holding the USD exchange-rate table. MUST match booking-service's cache name. */
    public static final String USD_EXCHANGE_RATES = "usdExchangeRates";

    /**
     * Default Redis cache config: configurable TTL (15m default) and a type-agnostic JSON serializer
     * bound to {@code List<ExchangeRateDto>} (the only cached value), so cart and booking read each
     * other's entry despite living in different packages.
     */
    @Bean
    RedisCacheConfiguration redisCacheConfiguration(
            @Value("${cart.exchange-rate.cache-ttl:15m}") Duration ttl) {
        ObjectMapper mapper = new ObjectMapper();
        JavaType rateListType = mapper.getTypeFactory()
                .constructCollectionType(List.class, ExchangeRateDto.class);
        Jackson2JsonRedisSerializer<Object> valueSerializer =
                new Jackson2JsonRedisSerializer<>(mapper, rateListType);
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(valueSerializer));
    }

    /** Never let a Redis outage break repricing — log the cache error and fall back to the source. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new SimpleCacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis cache GET failed (cache={}, key={}) — falling back to source: {}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key,
                                            Object value) {
                log.warn("Redis cache PUT failed (cache={}, key={}) — continuing without caching: {}",
                        cache.getName(), key, exception.getMessage());
            }
        };
    }
}
