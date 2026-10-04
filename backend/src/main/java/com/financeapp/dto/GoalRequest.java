package com.financeapp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class GoalRequest {

    @NotBlank(message = "Goal name is required")
    private String name;

    @NotNull(message = "Target amount is required")
    @DecimalMin(value = "0.01", message = "Target amount must be greater than 0")
    private BigDecimal targetAmount;

    @NotNull(message = "Current amount is required")
    @DecimalMin(value = "0.00", message = "Current amount cannot be negative")
    private BigDecimal currentAmount = BigDecimal.ZERO;

    private java.time.LocalDate targetDate;

    public GoalRequest() {}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getTargetAmount() {
        return targetAmount;
    }

    public void setTargetAmount(BigDecimal targetAmount) {
        this.targetAmount = targetAmount;
    }

    public BigDecimal getCurrentAmount() {
        return currentAmount;
    }

    public void setCurrentAmount(BigDecimal currentAmount) {
        this.currentAmount = currentAmount;
    }

    public java.time.LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(java.time.LocalDate targetDate) {
        this.targetDate = targetDate;
    }
}
