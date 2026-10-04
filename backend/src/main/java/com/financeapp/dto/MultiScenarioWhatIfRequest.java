package com.financeapp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class MultiScenarioWhatIfRequest {

    @NotNull(message = "Purchase amount is required")
    @DecimalMin(value = "0.01", message = "Purchase amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Description is required")
    private String description;

    @Min(value = 1, message = "Delay months must be at least 1")
    private int delayMonths = 3;

    @Min(value = 1, message = "EMI tenure months must be at least 1")
    @jakarta.validation.constraints.Max(value = 60, message = "EMI tenure months must not exceed 60")
    private int emiTenureMonths = 6;

    private BigDecimal emiMonthlyAmount; // Optional: auto-calculated as amount / tenure if null

    public MultiScenarioWhatIfRequest() {}

    public MultiScenarioWhatIfRequest(BigDecimal amount, String description, int delayMonths, int emiTenureMonths, BigDecimal emiMonthlyAmount) {
        this.amount = amount;
        this.description = description;
        this.delayMonths = delayMonths;
        this.emiTenureMonths = emiTenureMonths;
        this.emiMonthlyAmount = emiMonthlyAmount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDelayMonths() {
        return delayMonths;
    }

    public void setDelayMonths(int delayMonths) {
        this.delayMonths = delayMonths;
    }

    public int getEmiTenureMonths() {
        return emiTenureMonths;
    }

    public void setEmiTenureMonths(int emiTenureMonths) {
        this.emiTenureMonths = emiTenureMonths;
    }

    public BigDecimal getEmiMonthlyAmount() {
        return emiMonthlyAmount;
    }

    public void setEmiMonthlyAmount(BigDecimal emiMonthlyAmount) {
        this.emiMonthlyAmount = emiMonthlyAmount;
    }
}
