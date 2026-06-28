package com.atlas.cart.dto;

import java.math.BigDecimal;

public record MoneyDto(BigDecimal amount, String currency) {}
