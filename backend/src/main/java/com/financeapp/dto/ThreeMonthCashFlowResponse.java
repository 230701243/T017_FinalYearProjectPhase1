package com.financeapp.dto;

import java.math.BigDecimal;
import java.util.List;

public record ThreeMonthCashFlowResponse(
    BigDecimal startingBalance,
    BigDecimal expectedMonthlyIncome,
    BigDecimal recurringBills,
    List<MonthlyCashFlowProjection> projections
) {
    public record MonthlyCashFlowProjection(
        String monthName,
        BigDecimal startBalance,
        BigDecimal expectedIncome,
        BigDecimal forecastedExpenses,
        BigDecimal recurringBills,
        BigDecimal netCashFlow,
        BigDecimal endBalance
    ) {}
}
