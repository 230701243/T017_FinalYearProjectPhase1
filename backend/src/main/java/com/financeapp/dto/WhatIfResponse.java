package com.financeapp.dto;

import java.math.BigDecimal;
import java.util.List;

public record WhatIfResponse(
    String scenarioDescription,
    BigDecimal hypotheticalAmount,
    String hypotheticalDate,
    ProjectionState baseline,
    ProjectionState simulated,
    DiffSummary diff
) {
    public record ProjectionState(
        BigDecimal startingBalance,
        BigDecimal month3EndingBalance,
        int totalRiskScore,
        String riskLevel,
        List<ThreeMonthCashFlowResponse.MonthlyCashFlowProjection> projections
    ) {}

    public record DiffSummary(
        BigDecimal balanceImpact,
        BigDecimal month3EndingBalanceImpact,
        int riskScoreImpact,
        boolean riskLevelChanged,
        String riskLevelShift,
        String impactSummaryText,
        LocalizedMessage impactSummaryMessage
    ) {
        public DiffSummary(
            BigDecimal balanceImpact,
            BigDecimal month3EndingBalanceImpact,
            int riskScoreImpact,
            boolean riskLevelChanged,
            String riskLevelShift,
            String impactSummaryText
        ) {
            this(balanceImpact, month3EndingBalanceImpact, riskScoreImpact, riskLevelChanged, riskLevelShift, impactSummaryText, null);
        }
    }
}
