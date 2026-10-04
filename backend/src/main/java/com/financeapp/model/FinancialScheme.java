package com.financeapp.model;

/**
 * Static scheme definition for deterministic financial recommendations.
 * Interest rates, returns, premiums, and cover amounts are intentionally omitted
 * as facts; users are directed to official verified URLs for current terms.
 */
public record FinancialScheme(
        String id,
        String name,
        String type, // GOVERNMENT or BANK
        String category, // Insurance, Pension, Savings, Health
        String purpose,
        String generalEligibilityNote,
        String officialUrl,
        String lastVerified
) {}
