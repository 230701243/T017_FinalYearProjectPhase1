package com.financeapp.service;

import com.financeapp.dto.CurrencyConfigDto;
import com.financeapp.dto.CurrencyUpdateRequest;
import com.financeapp.model.CurrencyConfig;
import com.financeapp.repository.CurrencyConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CurrencyConfigService {

    private final CurrencyConfigRepository currencyConfigRepository;

    public CurrencyConfigService(CurrencyConfigRepository currencyConfigRepository) {
        this.currencyConfigRepository = currencyConfigRepository;
    }

    @Transactional(readOnly = true)
    public List<CurrencyConfigDto> getAllCurrencies() {
        return currencyConfigRepository.findAllByOrderByCurrencyCodeAsc()
                .stream()
                .map(CurrencyConfigDto::new)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CurrencyConfigDto getCurrencyByCode(String currencyCode) {
        CurrencyConfig config = currencyConfigRepository.findByCurrencyCodeIgnoreCase(currencyCode)
                .orElseThrow(() -> new IllegalArgumentException("Currency not found for code: " + currencyCode));
        return new CurrencyConfigDto(config);
    }

    @Transactional(readOnly = true)
    public CurrencyConfigDto getCurrencyByCountry(String countryCode) {
        CurrencyConfig config = currencyConfigRepository.findByCountryCodeIgnoreCase(countryCode)
                .orElseThrow(() -> new IllegalArgumentException("Currency not found for country code: " + countryCode));
        return new CurrencyConfigDto(config);
    }

    @Transactional
    public CurrencyConfigDto updateCurrency(String currencyCode, CurrencyUpdateRequest request) {
        CurrencyConfig config = currencyConfigRepository.findByCurrencyCodeIgnoreCase(currencyCode)
                .orElseGet(() -> {
                    CurrencyConfig newConfig = new CurrencyConfig();
                    newConfig.setCurrencyCode(currencyCode.toUpperCase());
                    return newConfig;
                });

        if (request.getCountryCode() != null && !request.getCountryCode().isBlank()) {
            config.setCountryCode(request.getCountryCode().toUpperCase());
        }
        if (request.getCurrencySymbol() != null && !request.getCurrencySymbol().isBlank()) {
            config.setCurrencySymbol(request.getCurrencySymbol());
        }
        if (request.getExchangeRateToUsd() != null) {
            if (request.getExchangeRateToUsd().compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Exchange rate must be greater than zero.");
            }
            config.setExchangeRateToUsd(request.getExchangeRateToUsd());
        }
        if (request.getCurrencyName() != null && !request.getCurrencyName().isBlank()) {
            config.setCurrencyName(request.getCurrencyName());
        }

        CurrencyConfig saved = currencyConfigRepository.save(config);
        return new CurrencyConfigDto(saved);
    }

    @Transactional(readOnly = true)
    public BigDecimal convert(BigDecimal amount, String fromCurrencyCode, String toCurrencyCode) {
        if (amount == null) {
            return BigDecimal.ZERO;
        }
        if (fromCurrencyCode.equalsIgnoreCase(toCurrencyCode)) {
            return amount.setScale(4, RoundingMode.HALF_UP);
        }

        CurrencyConfig fromConfig = currencyConfigRepository.findByCurrencyCodeIgnoreCase(fromCurrencyCode)
                .orElseThrow(() -> new IllegalArgumentException("Source currency not found: " + fromCurrencyCode));
        CurrencyConfig toConfig = currencyConfigRepository.findByCurrencyCodeIgnoreCase(toCurrencyCode)
                .orElseThrow(() -> new IllegalArgumentException("Target currency not found: " + toCurrencyCode));

        // Amount in USD = Amount / fromRateToUSD
        BigDecimal amountInUsd = amount.divide(fromConfig.getExchangeRateToUsd(), 10, RoundingMode.HALF_UP);
        // Target Amount = Amount in USD * toRateToUSD
        BigDecimal convertedAmount = amountInUsd.multiply(toConfig.getExchangeRateToUsd());

        return convertedAmount.setScale(4, RoundingMode.HALF_UP);
    }
}
