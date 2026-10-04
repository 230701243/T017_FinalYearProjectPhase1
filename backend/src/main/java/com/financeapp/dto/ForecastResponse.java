package com.financeapp.dto;

import java.math.BigDecimal;
import java.util.List;

public record ForecastResponse(
    String nextMonthName,
    BigDecimal variableProjectedExpense,
    BigDecimal recurringCommitmentsTotal,
    BigDecimal totalProjectedExpense,
    BigDecimal projectedNetCashFlow,
    String disclaimer,
    List<CategoryForecast> categoryForecasts,
    LocalizedMessage disclaimerMessage
) {
    public ForecastResponse(
        String nextMonthName,
        BigDecimal variableProjectedExpense,
        BigDecimal recurringCommitmentsTotal,
        BigDecimal totalProjectedExpense,
        BigDecimal projectedNetCashFlow,
        String disclaimer,
        List<CategoryForecast> categoryForecasts
    ) {
        this(nextMonthName, variableProjectedExpense, recurringCommitmentsTotal, totalProjectedExpense, projectedNetCashFlow, disclaimer, categoryForecasts, null);
    }

    public record CategoryForecast(
        String category,
        double slope,
        BigDecimal projectedAmount,
        String trendText,
        LocalizedMessage trendMessage
    ) {
        public CategoryForecast(String category, double slope, BigDecimal projectedAmount, String trendText) {
            this(category, slope, projectedAmount, trendText, null);
        }
    }
}
