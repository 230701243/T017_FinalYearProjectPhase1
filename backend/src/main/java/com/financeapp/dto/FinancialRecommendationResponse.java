package com.financeapp.dto;

import java.math.BigDecimal;
import java.util.List;

public record FinancialRecommendationResponse(
    String statusLevel, // SAFE, WARNING, CRITICAL
    String primaryRecommendation,
    BigDecimal reserveThreshold,
    boolean isBelowReserveThreshold,
    List<RecommendationItem> recommendationsList,
    LocalizedMessage primaryMessage
) {
    public FinancialRecommendationResponse(
        String statusLevel,
        String primaryRecommendation,
        BigDecimal reserveThreshold,
        boolean isBelowReserveThreshold,
        List<RecommendationItem> recommendationsList
    ) {
        this(statusLevel, primaryRecommendation, reserveThreshold, isBelowReserveThreshold, recommendationsList, null);
    }

    public record RecommendationItem(
        String title,
        String priority, // HIGH, MEDIUM, LOW
        String detail,
        String actionableStep,
        String category, // Insurance, Savings, Pension, Health
        String officialUrl,
        LocalizedMessage titleMessage,
        LocalizedMessage detailMessage,
        LocalizedMessage actionableStepMessage
    ) {
        public RecommendationItem(String title, String priority, String detail, String actionableStep, String category, String officialUrl) {
            this(title, priority, detail, actionableStep, category, officialUrl, null, null, null);
        }

        public RecommendationItem(String title, String priority, String detail, String actionableStep) {
            this(title, priority, detail, actionableStep, "Savings", null, null, null, null);
        }

        public RecommendationItem(String title, String priority, String detail, String actionableStep, String category) {
            this(title, priority, detail, actionableStep, category, null, null, null, null);
        }
    }
}
