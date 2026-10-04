package com.financeapp.dto;

import java.util.List;

public record RecategorizeSummary(
        int totalUncategorizedProcessed,
        int matchedCount,
        int unmatchedCount,
        List<RecategorizeDetail> matchedTransactions,
        List<RecategorizeDetail> unmatchedTransactions
) {
    public record RecategorizeDetail(
            Long id,
            String description,
            String previousCategory,
            String newCategory,
            String matchedRule,
            String confidenceSource
    ) {}
}
