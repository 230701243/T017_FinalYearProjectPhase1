package com.financeapp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class WhatIfRequest {

    @NotNull(message = "Purchase amount is required")
    @DecimalMin(value = "0.01", message = "Purchase amount must be greater than 0")
    private BigDecimal amount;

    private String date; // YYYY-MM-DD or null (defaults to current date)
    
    @NotBlank(message = "Description is required")
    private String description;

    public WhatIfRequest() {}

    public WhatIfRequest(BigDecimal amount, String date, String description) {
        this.amount = amount;
        this.date = date;
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
