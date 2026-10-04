package com.financeapp.dto;

import com.financeapp.model.CurrencyConfig;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CurrencyConfigDto {

    private Long id;
    private String countryCode;
    private String currencyCode;
    private String currencySymbol;
    private BigDecimal exchangeRateToUsd;
    private String currencyName;
    private LocalDateTime updatedAt;

    public CurrencyConfigDto() {}

    public CurrencyConfigDto(CurrencyConfig entity) {
        if (entity != null) {
            this.id = entity.getId();
            this.countryCode = entity.getCountryCode();
            this.currencyCode = entity.getCurrencyCode();
            this.currencySymbol = entity.getCurrencySymbol();
            this.exchangeRateToUsd = entity.getExchangeRateToUsd();
            this.currencyName = entity.getCurrencyName();
            this.updatedAt = entity.getUpdatedAt();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

    public String getCurrencyCode() {
        return currencyCode;
    }

    public void setCurrencyCode(String currencyCode) {
        this.currencyCode = currencyCode;
    }

    public String getCurrencySymbol() {
        return currencySymbol;
    }

    public void setCurrencySymbol(String currencySymbol) {
        this.currencySymbol = currencySymbol;
    }

    public BigDecimal getExchangeRateToUsd() {
        return exchangeRateToUsd;
    }

    public void setExchangeRateToUsd(BigDecimal exchangeRateToUsd) {
        this.exchangeRateToUsd = exchangeRateToUsd;
    }

    public String getCurrencyName() {
        return currencyName;
    }

    public void setCurrencyName(String currencyName) {
        this.currencyName = currencyName;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
