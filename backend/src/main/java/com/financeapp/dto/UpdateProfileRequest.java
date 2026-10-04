package com.financeapp.dto;

public class UpdateProfileRequest {

    private String country;
    private String preferredCurrency;
    private Boolean hasHealthInsurance;
    private Boolean hasLifeInsurance;

    public UpdateProfileRequest() {}

    public UpdateProfileRequest(String country, String preferredCurrency) {
        this.country = country;
        this.preferredCurrency = preferredCurrency;
    }

    public UpdateProfileRequest(String country, String preferredCurrency, Boolean hasHealthInsurance, Boolean hasLifeInsurance) {
        this.country = country;
        this.preferredCurrency = preferredCurrency;
        this.hasHealthInsurance = hasHealthInsurance;
        this.hasLifeInsurance = hasLifeInsurance;
    }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getPreferredCurrency() { return preferredCurrency; }
    public void setPreferredCurrency(String preferredCurrency) { this.preferredCurrency = preferredCurrency; }

    public Boolean getHasHealthInsurance() { return hasHealthInsurance; }
    public void setHasHealthInsurance(Boolean hasHealthInsurance) { this.hasHealthInsurance = hasHealthInsurance; }

    public Boolean getHasLifeInsurance() { return hasLifeInsurance; }
    public void setHasLifeInsurance(Boolean hasLifeInsurance) { this.hasLifeInsurance = hasLifeInsurance; }
}
