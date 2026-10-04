package com.financeapp.dto;

import java.util.List;

public record RiskScoreResponse(
    int totalRiskScore,
    String riskLevel, // LOW, MEDIUM, HIGH
    List<RiskFactor> contributingFactors
) {
    public record RiskFactor(
        String factor,
        int score,
        int maxScore,
        String status, // HEALTHY, MODERATE_RISK, HIGH_RISK
        String description,
        LocalizedMessage factorMessage,
        LocalizedMessage descriptionMessage
    ) {
        public RiskFactor(String factor, int score, int maxScore, String status, String description) {
            this(factor, score, maxScore, status, description, null, null);
        }
    }
}
