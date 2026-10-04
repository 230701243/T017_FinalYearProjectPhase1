package com.financeapp.dto;

public record CategorizationResult(
    String category,
    String matchedRule,
    String confidenceSource
) {}
