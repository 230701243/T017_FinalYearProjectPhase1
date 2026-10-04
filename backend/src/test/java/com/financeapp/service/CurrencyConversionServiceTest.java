package com.financeapp.service;

import com.financeapp.config.CurrencyDataInitializer;
import com.financeapp.model.User;
import com.financeapp.repository.CurrencyConfigRepository;
import com.financeapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class CurrencyConversionServiceTest {

    @Autowired
    private CurrencyConfigRepository currencyConfigRepository;

    @Autowired
    private CurrencyDataInitializer currencyDataInitializer;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CurrencyConversionService currencyConversionService;

    @BeforeEach
    void setUp() {
        currencyConfigRepository.deleteAll();
        userRepository.deleteAll();
        currencyDataInitializer.seedInitialCurrencies();
    }

    @Test
    void testConvertUsdToVariousCurrencies() {
        BigDecimal usd100 = new BigDecimal("100.00");

        // USD conversion (rate 1.0)
        var usdRes = currencyConversionService.convertUsd(usd100, "USD");
        assertEquals(new BigDecimal("100.00"), usdRes.convertedAmount());
        assertEquals("$", usdRes.currencySymbol());
        assertEquals("$100.00", usdRes.formatted());

        // INR conversion (rate 83.50)
        var inrRes = currencyConversionService.convertUsd(usd100, "INR");
        assertEquals(new BigDecimal("8350.00"), inrRes.convertedAmount());
        assertEquals("₹", inrRes.currencySymbol());
        assertEquals("₹8,350.00", inrRes.formatted());

        // EUR conversion (rate 0.92)
        var eurRes = currencyConversionService.convertUsd(usd100, "EUR");
        assertEquals(new BigDecimal("92.00"), eurRes.convertedAmount());
        assertEquals("€", eurRes.currencySymbol());
        assertEquals("€92.00", eurRes.formatted());

        // GBP conversion (rate 0.79)
        var gbpRes = currencyConversionService.convertUsd(usd100, "GBP");
        assertEquals(new BigDecimal("79.00"), gbpRes.convertedAmount());
        assertEquals("£", gbpRes.currencySymbol());
        assertEquals("£79.00", gbpRes.formatted());

        // JPY conversion (rate 155.00)
        var jpyRes = currencyConversionService.convertUsd(usd100, "JPY");
        assertEquals(new BigDecimal("15500.00"), jpyRes.convertedAmount());
        assertEquals("¥", jpyRes.currencySymbol());
        assertEquals("¥15,500.00", jpyRes.formatted());
    }

    @Test
    void testConvertUsdForUserWithPreferredCurrency() {
        User user = new User("rahul", "rahul@example.com", "pass", "IN", "INR");
        userRepository.save(user);

        var res = currencyConversionService.convertUsdForUser(new BigDecimal("50.00"), "rahul");
        assertEquals(new BigDecimal("4175.00"), res.convertedAmount());
        assertEquals("INR", res.currencyCode());
        assertEquals("₹", res.currencySymbol());
        assertEquals("₹4,175.00", res.formatted());
    }

    @Test
    void testFallbackForNullAndUnknownUser() {
        var res = currencyConversionService.convertUsdForUser(new BigDecimal("10.00"), "unknown_user");
        assertEquals(new BigDecimal("10.00"), res.convertedAmount());
        assertEquals("$", res.currencySymbol());
        assertEquals("$10.00", res.formatted());

        var nullRes = currencyConversionService.convertUsd(null, null);
        assertEquals(BigDecimal.ZERO.setScale(2), nullRes.convertedAmount());
        assertEquals("$0.00", nullRes.formatted());
    }
}
