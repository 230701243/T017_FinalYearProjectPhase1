package com.financeapp.service;

import com.financeapp.dto.MoneyParam;
import com.financeapp.model.CurrencyConfig;
import com.financeapp.model.User;
import com.financeapp.repository.CurrencyConfigRepository;
import com.financeapp.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Response/Display layer utility to convert USD amounts to a user's preferred currency
 * using the static database currency configuration table.
 *
 * IMPORTANT: This utility must only be applied at the presentation/response layer.
 * All internal calculation logic (Risk Scoring, ML Forecasting, Decision Scenario Evaluator)
 * must continue operating purely in standard USD.
 */
@Service
public class CurrencyConversionService {

    private final CurrencyConfigRepository currencyConfigRepository;
    private final UserRepository userRepository;

    public record ConvertedDisplay(
            BigDecimal originalUsdAmount,
            BigDecimal convertedAmount,
            String currencyCode,
            String currencySymbol,
            String formatted
    ) {}

    public CurrencyConversionService(CurrencyConfigRepository currencyConfigRepository, UserRepository userRepository) {
        this.currencyConfigRepository = currencyConfigRepository;
        this.userRepository = userRepository;
    }

    private static volatile CurrencyConversionService instance;

    @jakarta.annotation.PostConstruct
    public void init() {
        instance = this;
    }

    /**
     * Fallback configuration record for when database repo is empty or in unit tests.
     */
    private record FallbackCurrency(String symbol, BigDecimal rate) {}

    private static FallbackCurrency getFallback(String code) {
        if (code == null) return new FallbackCurrency("$", BigDecimal.ONE);
        return switch (code.trim().toUpperCase()) {
            case "INR" -> new FallbackCurrency("₹", new BigDecimal("83.500000"));
            case "EUR" -> new FallbackCurrency("€", new BigDecimal("0.920000"));
            case "GBP" -> new FallbackCurrency("£", new BigDecimal("0.790000"));
            case "JPY" -> new FallbackCurrency("¥", new BigDecimal("155.000000"));
            case "CAD" -> new FallbackCurrency("CA$", new BigDecimal("1.360000"));
            case "AUD" -> new FallbackCurrency("A$", new BigDecimal("1.520000"));
            case "SGD" -> new FallbackCurrency("S$", new BigDecimal("1.350000"));
            case "AED" -> new FallbackCurrency("د.إ", new BigDecimal("3.672500"));
            case "CHF" -> new FallbackCurrency("CHF", new BigDecimal("0.900000"));
            default -> new FallbackCurrency("$", BigDecimal.ONE);
        };
    }

    /**
     * Converts a USD amount to the given currency code and formats the result with the correct symbol.
     */
    public ConvertedDisplay convertUsd(BigDecimal usdAmount, String currencyCode) {
        BigDecimal safeUsd = usdAmount != null ? usdAmount : BigDecimal.ZERO;
        String code = (currencyCode != null && !currencyCode.isBlank()) ? currencyCode.trim().toUpperCase() : "USD";

        String symbol = "$";
        BigDecimal rate = BigDecimal.ONE;

        if (currencyConfigRepository != null) {
            var configOpt = currencyConfigRepository.findByCurrencyCodeIgnoreCase(code);
            if (configOpt.isPresent()) {
                CurrencyConfig config = configOpt.get();
                symbol = config.getCurrencySymbol() != null ? config.getCurrencySymbol() : "$";
                rate = config.getExchangeRateToUsd() != null ? config.getExchangeRateToUsd() : BigDecimal.ONE;
            } else {
                FallbackCurrency fb = getFallback(code);
                symbol = fb.symbol();
                rate = fb.rate();
            }
        } else {
            FallbackCurrency fb = getFallback(code);
            symbol = fb.symbol();
            rate = fb.rate();
        }

        BigDecimal converted = safeUsd.multiply(rate).setScale(2, RoundingMode.HALF_UP);

        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        String formatted = symbol + df.format(converted);

        return new ConvertedDisplay(safeUsd, converted, code, symbol, formatted);
    }

    /**
     * Formats a USD amount in the target currency (with symbol, commas, and 2 decimals).
     */
    public String formatAmount(BigDecimal usdAmount, String currencyCode) {
        return convertUsd(usdAmount, currencyCode).formatted();
    }

    public String formatAmount(double usdAmount, String currencyCode) {
        return formatAmount(BigDecimal.valueOf(usdAmount), currencyCode);
    }

    public String formatAmount(Number usdAmount, String currencyCode) {
        if (usdAmount == null) return formatAmount(BigDecimal.ZERO, currencyCode);
        if (usdAmount instanceof BigDecimal bd) return formatAmount(bd, currencyCode);
        return formatAmount(BigDecimal.valueOf(usdAmount.doubleValue()), currencyCode);
    }

    public String getCurrencySymbol(String currencyCode) {
        return convertUsd(BigDecimal.ZERO, currencyCode).currencySymbol();
    }

    /**
     * Static shared money-formatting helper callable from any service or unit test.
     */
    public static String formatMoney(BigDecimal usdAmount, String currencyCode) {
        if (instance != null) {
            return instance.formatAmount(usdAmount, currencyCode);
        }
        BigDecimal safeUsd = usdAmount != null ? usdAmount : BigDecimal.ZERO;
        String code = (currencyCode != null && !currencyCode.isBlank()) ? currencyCode.trim().toUpperCase() : "USD";
        FallbackCurrency fb = getFallback(code);
        BigDecimal converted = safeUsd.multiply(fb.rate()).setScale(2, RoundingMode.HALF_UP);
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        DecimalFormat df = new DecimalFormat("#,##0.00", symbols);
        return fb.symbol() + df.format(converted);
    }

    public MoneyParam toMoneyParam(BigDecimal usdAmount, String currencyCode) {
        ConvertedDisplay display = convertUsd(usdAmount, currencyCode);
        return new MoneyParam(display.convertedAmount(), display.currencyCode());
    }

    public static MoneyParam toMoney(BigDecimal usdAmount, String currencyCode) {
        if (instance != null) {
            return instance.toMoneyParam(usdAmount, currencyCode);
        }
        BigDecimal safeUsd = usdAmount != null ? usdAmount : BigDecimal.ZERO;
        String code = (currencyCode != null && !currencyCode.isBlank()) ? currencyCode.trim().toUpperCase() : "USD";
        FallbackCurrency fb = getFallback(code);
        BigDecimal converted = safeUsd.multiply(fb.rate()).setScale(2, RoundingMode.HALF_UP);
        return new MoneyParam(converted, code);
    }

    public static String formatMoney(double usdAmount, String currencyCode) {
        return formatMoney(BigDecimal.valueOf(usdAmount), currencyCode);
    }

    public static String formatMoney(Number usdAmount, String currencyCode) {
        if (usdAmount == null) return formatMoney(BigDecimal.ZERO, currencyCode);
        if (usdAmount instanceof BigDecimal bd) return formatMoney(bd, currencyCode);
        return formatMoney(BigDecimal.valueOf(usdAmount.doubleValue()), currencyCode);
    }

    /**
     * Looks up the user's preferred currency and converts the USD amount for display.
     */
    public ConvertedDisplay convertUsdForUser(BigDecimal usdAmount, String username) {
        String preferredCurrency = "USD";
        if (username != null && !username.isBlank() && userRepository != null) {
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null && user.getPreferredCurrency() != null && !user.getPreferredCurrency().isBlank()) {
                preferredCurrency = user.getPreferredCurrency();
            }
        }
        return convertUsd(usdAmount, preferredCurrency);
    }

    /**
     * Quick helper to return formatted string with symbol for a user.
     */
    public String formatUsdForUser(BigDecimal usdAmount, String username) {
        return convertUsdForUser(usdAmount, username).formatted();
    }
}
