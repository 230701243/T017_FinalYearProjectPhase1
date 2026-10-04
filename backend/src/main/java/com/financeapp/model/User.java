package com.financeapp.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    private String password;

    private String country = "US";

    private String preferredCurrency = "USD";

    private String preferredLanguage = "en";

    private Boolean hasHealthInsurance = false;

    private Boolean hasLifeInsurance = false;

    public User() {}

    public User(String username, String email, String password) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.country = "US";
        this.preferredCurrency = "USD";
        this.hasHealthInsurance = false;
        this.hasLifeInsurance = false;
    }

    public User(String username, String email, String password, String country, String preferredCurrency) {
        this.username = username;
        this.email = email;
        this.password = password;
        this.country = (country != null && !country.isBlank()) ? country : "US";
        this.preferredCurrency = (preferredCurrency != null && !preferredCurrency.isBlank()) ? preferredCurrency : "USD";
        this.hasHealthInsurance = false;
        this.hasLifeInsurance = false;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getCountry() {
        return (country != null && !country.isBlank()) ? country : "US";
    }

    public void setCountry(String country) {
        this.country = (country != null && !country.isBlank()) ? country : "US";
    }

    public String getPreferredCurrency() {
        return (preferredCurrency != null && !preferredCurrency.isBlank()) ? preferredCurrency : "USD";
    }

    public void setPreferredCurrency(String preferredCurrency) {
        this.preferredCurrency = (preferredCurrency != null && !preferredCurrency.isBlank()) ? preferredCurrency : "USD";
    }

    public String getPreferredLanguage() {
        return (preferredLanguage != null && !preferredLanguage.isBlank()) ? preferredLanguage : "en";
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = (preferredLanguage != null && !preferredLanguage.isBlank()) ? preferredLanguage : "en";
    }

    public Boolean getHasHealthInsurance() {
        return hasHealthInsurance != null ? hasHealthInsurance : false;
    }

    public void setHasHealthInsurance(Boolean hasHealthInsurance) {
        this.hasHealthInsurance = hasHealthInsurance != null ? hasHealthInsurance : false;
    }

    public Boolean getHasLifeInsurance() {
        return hasLifeInsurance != null ? hasLifeInsurance : false;
    }

    public void setHasLifeInsurance(Boolean hasLifeInsurance) {
        this.hasLifeInsurance = hasLifeInsurance != null ? hasLifeInsurance : false;
    }
}
