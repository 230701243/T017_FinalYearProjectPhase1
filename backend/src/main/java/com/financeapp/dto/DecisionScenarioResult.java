package com.financeapp.dto;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public record DecisionScenarioResult(
        boolean feasible,
        BigDecimal projectedSavingsOrBudget,
        List<String> warnings,
        Map<String, Object> rawNumbers,
        List<LocalizedMessage> warningMessages
) {
    public DecisionScenarioResult(
            boolean feasible,
            BigDecimal projectedSavingsOrBudget,
            List<String> warnings,
            Map<String, Object> rawNumbers
    ) {
        this(feasible, projectedSavingsOrBudget, warnings, rawNumbers, Collections.emptyList());
    }
}
