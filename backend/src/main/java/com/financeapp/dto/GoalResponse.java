package com.financeapp.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record GoalResponse(
    Long id,
    String name,
    BigDecimal targetAmount,
    BigDecimal currentAmount,
    BigDecimal remainingAmount,
    double progressPercentage,
    double estimatedMonthsRemaining,
    String estimatedCompletionDate,
    LocalDate targetDate,
    LocalDateTime createdAt,
    LocalizedMessage estimatedCompletionMessage
) {
    public GoalResponse(
        Long id,
        String name,
        BigDecimal targetAmount,
        BigDecimal currentAmount,
        BigDecimal remainingAmount,
        double progressPercentage,
        double estimatedMonthsRemaining,
        String estimatedCompletionDate,
        LocalDate targetDate,
        LocalDateTime createdAt
    ) {
        this(id, name, targetAmount, currentAmount, remainingAmount, progressPercentage, estimatedMonthsRemaining, estimatedCompletionDate, targetDate, createdAt, null);
    }

    public GoalResponse(
        Long id,
        String name,
        BigDecimal targetAmount,
        BigDecimal currentAmount,
        BigDecimal remainingAmount,
        double progressPercentage,
        double estimatedMonthsRemaining,
        String estimatedCompletionDate,
        LocalDateTime createdAt
    ) {
        this(id, name, targetAmount, currentAmount, remainingAmount, progressPercentage, estimatedMonthsRemaining, estimatedCompletionDate, null, createdAt, null);
    }
}
