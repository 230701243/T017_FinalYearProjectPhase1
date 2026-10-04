package com.financeapp.dto;

import java.math.BigDecimal;

public record MoneyParam(
    BigDecimal amount,
    String currency
) {}
