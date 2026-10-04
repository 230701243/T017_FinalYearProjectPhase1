package com.financeapp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DecisionAssistantRequest {

    private String question;
    private HypotheticalPurchase hypotheticalPurchase;

    // Optional flat fields if the client passes them directly at top level
    private BigDecimal amount;
    private String description;
    private String timing;
    private String date;
    private BigDecimal emiMonthlyAmount;
    private Integer emiTenureMonths;
    private Integer delayMonths;
    private String languageCode;
    private String languageName;

    public DecisionAssistantRequest() {}

    public DecisionAssistantRequest(String question, HypotheticalPurchase hypotheticalPurchase) {
        this.question = question;
        this.hypotheticalPurchase = hypotheticalPurchase;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public HypotheticalPurchase getHypotheticalPurchase() {
        if (hypotheticalPurchase != null) {
            return hypotheticalPurchase;
        }
        if (amount != null || description != null || timing != null || date != null || emiMonthlyAmount != null || emiTenureMonths != null) {
            return new HypotheticalPurchase(
                    amount,
                    description != null ? description : "Hypothetical Purchase",
                    timing != null ? timing : (date != null ? date : "immediate"),
                    emiMonthlyAmount,
                    emiTenureMonths != null ? emiTenureMonths : 6,
                    delayMonths != null ? delayMonths : 3
            );
        }
        return null;
    }

    public void setHypotheticalPurchase(HypotheticalPurchase hypotheticalPurchase) {
        this.hypotheticalPurchase = hypotheticalPurchase;
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

    public String getTiming() {
        return timing;
    }

    public void setTiming(String timing) {
        this.timing = timing;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public BigDecimal getEmiMonthlyAmount() {
        return emiMonthlyAmount;
    }

    public void setEmiMonthlyAmount(BigDecimal emiMonthlyAmount) {
        this.emiMonthlyAmount = emiMonthlyAmount;
    }

    public Integer getEmiTenureMonths() {
        return emiTenureMonths;
    }

    public void setEmiTenureMonths(Integer emiTenureMonths) {
        this.emiTenureMonths = emiTenureMonths;
    }

    public Integer getDelayMonths() {
        return delayMonths;
    }

    public void setDelayMonths(Integer delayMonths) {
        this.delayMonths = delayMonths;
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public void setLanguageCode(String languageCode) {
        this.languageCode = languageCode;
    }

    public String getLanguageName() {
        return languageName;
    }

    public void setLanguageName(String languageName) {
        this.languageName = languageName;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class HypotheticalPurchase {
        private BigDecimal amount;
        private String description;
        private String timing;
        private BigDecimal emiMonthlyAmount;
        private Integer emiTenureMonths = 6;
        private Integer delayMonths = 3;

        public HypotheticalPurchase() {}

        public HypotheticalPurchase(BigDecimal amount, String description, String timing) {
            this.amount = amount;
            this.description = description;
            this.timing = timing;
            this.emiTenureMonths = 6;
            this.delayMonths = 3;
        }

        public HypotheticalPurchase(BigDecimal amount, String description, String timing, BigDecimal emiMonthlyAmount, Integer emiTenureMonths, Integer delayMonths) {
            this.amount = amount;
            this.description = description;
            this.timing = timing;
            this.emiMonthlyAmount = emiMonthlyAmount;
            this.emiTenureMonths = emiTenureMonths != null ? emiTenureMonths : 6;
            this.delayMonths = delayMonths != null ? delayMonths : 3;
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

        public String getTiming() {
            return timing;
        }

        public void setTiming(String timing) {
            this.timing = timing;
        }

        public BigDecimal getEmiMonthlyAmount() {
            return emiMonthlyAmount;
        }

        public void setEmiMonthlyAmount(BigDecimal emiMonthlyAmount) {
            this.emiMonthlyAmount = emiMonthlyAmount;
        }

        public Integer getEmiTenureMonths() {
            return emiTenureMonths;
        }

        public void setEmiTenureMonths(Integer emiTenureMonths) {
            this.emiTenureMonths = emiTenureMonths;
        }

        public Integer getDelayMonths() {
            return delayMonths;
        }

        public void setDelayMonths(Integer delayMonths) {
            this.delayMonths = delayMonths;
        }
    }
}
