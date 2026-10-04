package com.financeapp.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "currency_configs")
public class CurrencyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "country_code", nullable = false, length = 10)
    private String countryCode;

    @Column(name = "currency_code", nullable = false, unique = true, length = 10)
    private String currencyCode;

    @Column(name = "currency_symbol", nullable = false, length = 10)
    private String currencySymbol;

    @Column(name = "exchange_rate_to_usd", nullable = false, precision = 18, scale = 6)
    private BigDecimal exchangeRateToUsd; // e.g. 1 USD = X Currency units (USD: 1.0, INR: 83.50, EUR: 0.92, GBP: 0.79, JPY: 155.00)

    @Column(name = "currency_name", length = 50)
    private String currencyName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public CurrencyConfig() {}

    public CurrencyConfig(String countryCode, String currencyCode, String currencySymbol, BigDecimal exchangeRateToUsd, String currencyName) {
        this.countryCode = countryCode;
        this.currencyCode = currencyCode;
        this.currencySymbol = currencySymbol;
        this.exchangeRateToUsd = exchangeRateToUsd;
        this.currencyName = currencyName;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
