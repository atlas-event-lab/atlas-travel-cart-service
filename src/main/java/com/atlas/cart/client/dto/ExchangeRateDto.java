package com.atlas.cart.client.dto;

import java.math.BigDecimal;

public record ExchangeRateDto(String base, String quote, BigDecimal rate) {}
