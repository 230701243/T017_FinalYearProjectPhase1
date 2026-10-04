package com.financeapp.service;

import com.financeapp.config.CurrencyDataInitializer;
import com.financeapp.dto.CurrencyConfigDto;
import com.financeapp.dto.CurrencyUpdateRequest;
import com.financeapp.model.CurrencyConfig;
import com.financeapp.repository.CurrencyConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class CurrencyConfigServiceTest {

    @Autowired
    private CurrencyConfigRepository currencyConfigRepository;

    @Autowired
    private CurrencyDataInitializer currencyDataInitializer;

    @Autowired
    private CurrencyConfigService currencyConfigService;

    @BeforeEach
    void setUp() {
        currencyConfigRepository.deleteAll();
        currencyDataInitializer.seedInitialCurrencies();
    }

    @Test
    void testStaticSeedDatasetContainsRequiredCurrencies() {
        List<CurrencyConfigDto> currencies = currencyConfigService.getAllCurrencies();
        assertFalse(currencies.isEmpty());

        // Verify USD
        CurrencyConfigDto usd = currencyConfigService.getCurrencyByCode("USD");
        assertNotNull(usd);
        assertEquals("US", usd.getCountryCode());
        assertEquals("$", usd.getCurrencySymbol());
        assertEquals(new BigDecimal("1.000000"), usd.getExchangeRateToUsd());

        // Verify INR
        CurrencyConfigDto inr = currencyConfigService.getCurrencyByCode("INR");
        assertNotNull(inr);
        assertEquals("IN", inr.getCountryCode());
        assertEquals("₹", inr.getCurrencySymbol());
        assertEquals(new BigDecimal("83.500000"), inr.getExchangeRateToUsd());

        // Verify EUR
        CurrencyConfigDto eur = currencyConfigService.getCurrencyByCode("EUR");
        assertNotNull(eur);
        assertEquals("EU", eur.getCountryCode());
        assertEquals("€", eur.getCurrencySymbol());
        assertEquals(new BigDecimal("0.920000"), eur.getExchangeRateToUsd());

        // Verify GBP
        CurrencyConfigDto gbp = currencyConfigService.getCurrencyByCode("GBP");
        assertNotNull(gbp);
        assertEquals("GB", gbp.getCountryCode());
        assertEquals("£", gbp.getCurrencySymbol());
        assertEquals(new BigDecimal("0.790000"), gbp.getExchangeRateToUsd());

        // Verify JPY
        CurrencyConfigDto jpy = currencyConfigService.getCurrencyByCode("JPY");
        assertNotNull(jpy);
        assertEquals("JP", jpy.getCountryCode());
        assertEquals("¥", jpy.getCurrencySymbol());
        assertEquals(new BigDecimal("155.000000"), jpy.getExchangeRateToUsd());
    }

    @Test
    void testLookupByCountryCode() {
        CurrencyConfigDto inrByCountry = currencyConfigService.getCurrencyByCountry("IN");
        assertEquals("INR", inrByCountry.getCurrencyCode());
        assertEquals("₹", inrByCountry.getCurrencySymbol());

        CurrencyConfigDto jpyByCountry = currencyConfigService.getCurrencyByCountry("JP");
        assertEquals("JPY", jpyByCountry.getCurrencyCode());
        assertEquals("¥", jpyByCountry.getCurrencySymbol());
    }

    @Test
    void testManualRateUpdate() {
        CurrencyUpdateRequest updateReq = new CurrencyUpdateRequest(
                "IN",
                "₹",
                new BigDecimal("84.250000"),
                "Indian Rupee (Updated)"
        );

        CurrencyConfigDto updated = currencyConfigService.updateCurrency("INR", updateReq);
        assertEquals(new BigDecimal("84.250000"), updated.getExchangeRateToUsd());
        assertEquals("Indian Rupee (Updated)", updated.getCurrencyName());

        // Verify persistence
        CurrencyConfigDto fetched = currencyConfigService.getCurrencyByCode("INR");
        assertEquals(new BigDecimal("84.250000"), fetched.getExchangeRateToUsd());
    }

    @Test
    void testCurrencyConversion() {
        // 100 USD to INR (rate 83.50) -> 8350.0000
        BigDecimal usdToInr = currencyConfigService.convert(new BigDecimal("100"), "USD", "INR");
        assertEquals(new BigDecimal("8350.0000"), usdToInr);

        // Same currency conversion
        BigDecimal usdToUsd = currencyConfigService.convert(new BigDecimal("50"), "USD", "USD");
        assertEquals(new BigDecimal("50.0000"), usdToUsd);
    }
}
