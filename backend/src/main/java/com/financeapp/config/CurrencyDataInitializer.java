package com.financeapp.config;

import com.financeapp.model.CurrencyConfig;
import com.financeapp.repository.CurrencyConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Seeds static currency configurations (Country Code -> Currency Code -> Currency Symbol -> Exchange Rate to USD)
 * upon application startup if not already populated.
 * No external APIs are called; all seed data is fixed constants that can be updated manually later.
 */
@Component
public class CurrencyDataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(CurrencyDataInitializer.class);

    private final CurrencyConfigRepository currencyConfigRepository;

    public static final List<SeedCurrency> STATIC_CURRENCY_SEEDS = List.of(
        new SeedCurrency("US", "USD", "$", new BigDecimal("1.000000"), "US Dollar"),
        new SeedCurrency("IN", "INR", "₹", new BigDecimal("83.500000"), "Indian Rupee"),
        new SeedCurrency("EU", "EUR", "€", new BigDecimal("0.920000"), "Euro"),
        new SeedCurrency("GB", "GBP", "£", new BigDecimal("0.790000"), "British Pound"),
        new SeedCurrency("JP", "JPY", "¥", new BigDecimal("155.000000"), "Japanese Yen"),
        new SeedCurrency("CA", "CAD", "CA$", new BigDecimal("1.360000"), "Canadian Dollar"),
        new SeedCurrency("AU", "AUD", "A$", new BigDecimal("1.520000"), "Australian Dollar"),
        new SeedCurrency("SG", "SGD", "S$", new BigDecimal("1.350000"), "Singapore Dollar"),
        new SeedCurrency("AE", "AED", "د.إ", new BigDecimal("3.672500"), "UAE Dirham"),
        new SeedCurrency("CH", "CHF", "CHF", new BigDecimal("0.900000"), "Swiss Franc")
    );

    public record SeedCurrency(String countryCode, String currencyCode, String symbol, BigDecimal exchangeRateToUsd, String name) {}

    public CurrencyDataInitializer(CurrencyConfigRepository currencyConfigRepository) {
        this.currencyConfigRepository = currencyConfigRepository;
    }

    @Override
    public void run(String... args) {
        seedInitialCurrencies();
    }

    public void seedInitialCurrencies() {
        for (SeedCurrency seed : STATIC_CURRENCY_SEEDS) {
            if (!currencyConfigRepository.existsByCurrencyCodeIgnoreCase(seed.currencyCode())) {
                CurrencyConfig entity = new CurrencyConfig(
                    seed.countryCode(),
                    seed.currencyCode().toUpperCase(),
                    seed.symbol(),
                    seed.exchangeRateToUsd(),
                    seed.name()
                );
                currencyConfigRepository.save(entity);
                logger.info("Seeded currency config: {} ({}) [{}] with rate {}", seed.currencyCode(), seed.countryCode(), seed.symbol(), seed.exchangeRateToUsd());
            }
        }
        logger.info("Currency configuration static seed dataset check completed. Total currencies in DB: {}", currencyConfigRepository.count());
    }
}
