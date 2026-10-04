package com.financeapp.dto;

import com.financeapp.model.User;

public class UserProfileDto {

    private String username;
    private String email;
    private String country;
    private String preferredCurrency;
    private String preferredLanguage = "en";
    private Boolean hasHealthInsurance;
    private Boolean hasLifeInsurance;

    public UserProfileDto() {}

    public UserProfileDto(User user) {
        if (user != null) {
            this.username = user.getUsername();
            this.email = user.getEmail();
            this.country = user.getCountry();
            this.preferredCurrency = user.getPreferredCurrency();
            this.preferredLanguage = user.getPreferredLanguage();
            this.hasHealthInsurance = user.getHasHealthInsurance();
            this.hasLifeInsurance = user.getHasLifeInsurance();
        }
    }

    public UserProfileDto(String username, String email, String country, String preferredCurrency) {
        this(username, email, country, preferredCurrency, false, false);
    }

    public UserProfileDto(String username, String email, String country, String preferredCurrency, Boolean hasHealthInsurance, Boolean hasLifeInsurance) {
        this.username = username;
        this.email = email;
        this.country = country;
        this.preferredCurrency = preferredCurrency;
        this.hasHealthInsurance = hasHealthInsurance != null ? hasHealthInsurance : false;
        this.hasLifeInsurance = hasLifeInsurance != null ? hasLifeInsurance : false;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }

    public String getPreferredCurrency() { return preferredCurrency; }
    public void setPreferredCurrency(String preferredCurrency) { this.preferredCurrency = preferredCurrency; }

    public String getPreferredLanguage() { return (preferredLanguage != null && !preferredLanguage.isBlank()) ? preferredLanguage : "en"; }
    public void setPreferredLanguage(String preferredLanguage) { this.preferredLanguage = (preferredLanguage != null && !preferredLanguage.isBlank()) ? preferredLanguage : "en"; }

    public Boolean getHasHealthInsurance() { return hasHealthInsurance; }
    public void setHasHealthInsurance(Boolean hasHealthInsurance) { this.hasHealthInsurance = hasHealthInsurance; }

    public Boolean getHasLifeInsurance() { return hasLifeInsurance; }
    public void setHasLifeInsurance(Boolean hasLifeInsurance) { this.hasLifeInsurance = hasLifeInsurance; }
}
