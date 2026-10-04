package com.financeapp.dto;

import java.math.BigDecimal;

public class CurrencyUpdateRequest {

    private String countryCode;
    private String currencySymbol;
    private BigDecimal exchangeRateToUsd;
    private String currencyName;

    public CurrencyUpdateRequest() {}

    public CurrencyUpdateRequest(String countryCode, String currencySymbol, BigDecimal exchangeRateToUsd, String currencyName) {
        this.countryCode = countryCode;
        this.currencySymbol = currencySymbol;
        this.exchangeRateToUsd = exchangeRateToUsd;
        this.currencyName = currencyName;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
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
}
