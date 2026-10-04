package com.financeapp.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DecisionAssistantResponse(
        String question,
        DecisionAssistantRequest.HypotheticalPurchase hypotheticalPurchase,
        FinancialSnapshot financialSnapshot,
        RiskScoreResponse riskScore,
        List<GoalResponse> activeSavingsGoals,
        List<RecentTransactionSummary> recentTransactions,
        WhatIfResponse whatIfSimulation,
        ScenarioEvaluations scenarioEvaluations,
        FinancialRecommendationResponse recommendationSummary,
        String llmRecommendation,
        LocalDateTime assembledAt
) {
    public record FinancialSnapshot(
            BigDecimal totalBalance,
            BigDecimal totalIncome,
            BigDecimal totalExpenses,
            BigDecimal monthlyIncome,
            BigDecimal monthlyExpenses,
            BigDecimal monthlySavings,
            double savingsRatio,
            BigDecimal recurringBillsMonthly,
            List<DashboardStatsResponse.CategoryShare> categoryBreakdown
    ) {}

    public record RecentTransactionSummary(
            Long id,
            String type,
            String description,
            BigDecimal amount,
            String category,
            String date
    ) {}

    public record ScenarioEvaluations(
            DecisionScenarioResult buyNow,
            DecisionScenarioResult waitOption,
            DecisionScenarioResult emiOption,
            DecisionScenarioResult customTimingOption
    ) {
        public ScenarioEvaluations(DecisionScenarioResult buyNow, DecisionScenarioResult waitOption, DecisionScenarioResult emiOption) {
            this(buyNow, waitOption, emiOption, null);
        }
    }

    public DecisionAssistantResponse(
            String question,
            DecisionAssistantRequest.HypotheticalPurchase hypotheticalPurchase,
            FinancialSnapshot financialSnapshot,
            RiskScoreResponse riskScore,
            List<GoalResponse> activeSavingsGoals,
            WhatIfResponse whatIfSimulation,
            ScenarioEvaluations scenarioEvaluations,
            FinancialRecommendationResponse recommendationSummary,
            String llmRecommendation,
            LocalDateTime assembledAt
    ) {
        this(question, hypotheticalPurchase, financialSnapshot, riskScore, activeSavingsGoals, java.util.Collections.emptyList(), whatIfSimulation, scenarioEvaluations, recommendationSummary, llmRecommendation, assembledAt);
    }
}
