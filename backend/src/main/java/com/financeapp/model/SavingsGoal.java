package com.financeapp.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "savings_goals")
public class SavingsGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "target_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal targetAmount;

    @Column(name = "current_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal currentAmount = BigDecimal.ZERO;

    @Column(name = "target_date")
    private java.time.LocalDate targetDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private String username;

    public SavingsGoal() {}

    public SavingsGoal(String name, BigDecimal targetAmount, BigDecimal currentAmount, String username) {
        this(name, targetAmount, currentAmount, null, username);
    }

    public SavingsGoal(String name, BigDecimal targetAmount, BigDecimal currentAmount, java.time.LocalDate targetDate, String username) {
        this.name = name;
        this.targetAmount = targetAmount;
        this.currentAmount = currentAmount != null ? currentAmount : BigDecimal.ZERO;
        this.targetDate = targetDate;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public java.time.LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(java.time.LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}
