package com.financeapp.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class UpdateTransactionRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.00000001", inclusive = true, message = "Amount must be greater than 0")
    private BigDecimal amount;

    @NotBlank(message = "Description is required")
    private String description;

    @NotBlank(message = "Category is required")
    private String category;

    private String paymentMethod;
    private String source;
    private Boolean amountModified;

    public UpdateTransactionRequest() {}

    public UpdateTransactionRequest(BigDecimal amount, String description, String category) {
        this.amount = amount;
        this.description = description;
        this.category = category;
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

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Boolean getAmountModified() {
        return amountModified;
    }

    public void setAmountModified(Boolean amountModified) {
        this.amountModified = amountModified;
    }
}
