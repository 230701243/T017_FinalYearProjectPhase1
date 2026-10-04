package com.financeapp.dto;

import java.math.BigDecimal;

public record MultiScenarioWhatIfResponse(
    String scenarioDescription,
    BigDecimal totalAmount,
    int delayMonths,
    int emiTenureMonths,
    BigDecimal emiMonthlyAmount,
    WhatIfResponse.ProjectionState baseline,
    NamedScenarioResult buyNow,
    NamedScenarioResult buyInNMonths,
    NamedScenarioResult buyViaEMI,
    String recommendationText,
    DecisionScenarioResult emiEvaluation,
    LocalizedMessage recommendationMessage
) {
    public record NamedScenarioResult(
        String scenarioName,
        String scenarioLabel,
        WhatIfResponse.ProjectionState projectionState,
        WhatIfResponse.DiffSummary diff
    ) {}

    public MultiScenarioWhatIfResponse(
        String scenarioDescription,
        BigDecimal totalAmount,
        int delayMonths,
        int emiTenureMonths,
        BigDecimal emiMonthlyAmount,
        WhatIfResponse.ProjectionState baseline,
        NamedScenarioResult buyNow,
        NamedScenarioResult buyInNMonths,
        NamedScenarioResult buyViaEMI,
        String recommendationText
    ) {
        this(scenarioDescription, totalAmount, delayMonths, emiTenureMonths, emiMonthlyAmount, baseline, buyNow, buyInNMonths, buyViaEMI, recommendationText, null, null);
    }

    public MultiScenarioWhatIfResponse(
        String scenarioDescription,
        BigDecimal totalAmount,
        int delayMonths,
        int emiTenureMonths,
        BigDecimal emiMonthlyAmount,
        WhatIfResponse.ProjectionState baseline,
        NamedScenarioResult buyNow,
        NamedScenarioResult buyInNMonths,
        NamedScenarioResult buyViaEMI,
        String recommendationText,
        DecisionScenarioResult emiEvaluation
    ) {
        this(scenarioDescription, totalAmount, delayMonths, emiTenureMonths, emiMonthlyAmount, baseline, buyNow, buyInNMonths, buyViaEMI, recommendationText, emiEvaluation, null);
    }
}
