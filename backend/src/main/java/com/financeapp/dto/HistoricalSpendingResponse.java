package com.financeapp.dto;

import java.math.BigDecimal;
import java.util.List;

public record HistoricalSpendingResponse(
    double momTrendPercentage,
    String trendDirection, // UP, DOWN, STABLE
    List<MonthlyExpenseTotal> history
) {
    public record MonthlyExpenseTotal(
        String month,
        String yearMonth,
        BigDecimal totalAmount
    ) {}
}
