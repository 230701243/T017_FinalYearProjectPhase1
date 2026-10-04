package com.financeapp.dto;

import jakarta.validation.constraints.NotBlank;

public class RecategorizeRequest {

    @NotBlank(message = "Category is required")
    private String category;

    public RecategorizeRequest() {}

    public RecategorizeRequest(String category) {
        this.category = category;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
