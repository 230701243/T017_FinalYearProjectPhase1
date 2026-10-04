package com.financeapp.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 19, scale = 8)
    private BigDecimal amount;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(name = "payment_method", nullable = false)
    private String paymentMethod; // UPI, Cash, Credit

    @Column(nullable = false)
    private String category; // Food & Dining, Shopping & Tech, Uncategorized, etc.

    @Column(name = "matched_rule")
    private String matchedRule; // e.g. "Keyword: grocery" or null

    @Column(name = "confidence_source", nullable = false)
    private String confidenceSource; // RULE, ML, MANUAL, UNASSIGNED

    @Column(name = "is_manually_categorized", nullable = false)
    private boolean isManuallyCategorized = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private String username;

    public Transaction() {}

    public Transaction(BigDecimal amount, String description, String paymentMethod, String username) {
        this.amount = amount;
        this.description = description;
        this.paymentMethod = paymentMethod;
        this.username = username;
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getMatchedRule() {
        return matchedRule;
    }

    public void setMatchedRule(String matchedRule) {
        this.matchedRule = matchedRule;
    }

    public String getConfidenceSource() {
        return confidenceSource;
    }

    public void setConfidenceSource(String confidenceSource) {
        this.confidenceSource = confidenceSource;
    }

    public boolean isManuallyCategorized() {
        return isManuallyCategorized;
    }

    public void setManuallyCategorized(boolean manuallyCategorized) {
        isManuallyCategorized = manuallyCategorized;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
