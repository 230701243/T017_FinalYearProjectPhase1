package com.financeapp.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardStatsResponse(
    BigDecimal totalBalance,
    BigDecimal monthlyIncome,
    BigDecimal monthlyExpenses,
    BigDecimal savings,
    double savingsRatio,
    List<CategoryShare> categoryBreakdown
) {
    public record CategoryShare(
        String category,
        BigDecimal amount,
        double percentage
    ) {}
}
