package com.atlas.cart.scheduler;

import com.atlas.cart.repository.CartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class CartSweep {

    private final CartRepository cartRepository;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${atlas.cart.sweep-interval-ms:60000}")
    @Transactional
    public void sweep() {
        int expired = cartRepository.expireStaleCartsBeforeThreshold(Instant.now(clock));
        if (expired > 0) {
            log.info("Cart sweep expired {} stale cart(s)", expired);
        }
    }
}
