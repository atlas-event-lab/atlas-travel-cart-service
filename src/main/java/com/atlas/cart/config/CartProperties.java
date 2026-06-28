package com.atlas.cart.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

@Setter
@Getter
@ConfigurationProperties(prefix = "atlas.cart")
public class CartProperties {

    @DurationUnit(ChronoUnit.MINUTES)
    private Duration ttl = Duration.ofMinutes(30);

    private long sweepIntervalMs = 60_000L;

}
