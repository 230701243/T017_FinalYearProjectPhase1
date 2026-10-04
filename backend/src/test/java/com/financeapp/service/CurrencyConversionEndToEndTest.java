package com.financeapp.service;

import com.financeapp.config.CurrencyDataInitializer;
import com.financeapp.dto.RegisterRequest;
import com.financeapp.dto.UpdateProfileRequest;
import com.financeapp.dto.UserProfileDto;
import com.financeapp.model.Transaction;
import com.financeapp.model.User;
import com.financeapp.repository.CurrencyConfigRepository;
import com.financeapp.repository.TransactionRepository;
import com.financeapp.repository.UserRepository;
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
public class CurrencyConversionEndToEndTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CurrencyConfigRepository currencyConfigRepository;

    @Autowired
    private CurrencyDataInitializer currencyDataInitializer;

    @Autowired
    private AuthService authService;

    @Autowired
    private CurrencyConversionService currencyConversionService;

    private static final String TEST_USERNAME = "currency_tester";

    @Autowired
    private com.financeapp.repository.IncomeRepository incomeRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        transactionRepository.deleteAll();
        incomeRepository.deleteAll();
        currencyConfigRepository.deleteAll();
        currencyDataInitializer.seedInitialCurrencies();
    }

    @Test
    void testInrIncomeSubmissionAndDisplayConsistency() {
        // 1. Register a user with preferred currency INR
        RegisterRequest registerRequest = new RegisterRequest(
                "inr_user",
                "inr_user@example.com",
                "password123",
                "IN",
                "INR"
        );
        authService.register(registerRequest);

        UserProfileDto profile = authService.getProfile("inr_user");
        assertEquals("INR", profile.getPreferredCurrency());

        // 2. User enters 50,000 INR in the UI.
        // The frontend converts toUsd(50000, "INR") = 50000 / 83.50 = 598.802395 USD
        BigDecimal enteredInr = new BigDecimal("50000.00");
        BigDecimal exchangeRate = new BigDecimal("83.50");
        BigDecimal storedUsd = enteredInr.divide(exchangeRate, 6, java.math.RoundingMode.HALF_UP);

        com.financeapp.model.Income income = new com.financeapp.model.Income();
        income.setAmount(storedUsd);
        income.setDescription("Monthly Salary");
        income.setSource("Salary");
        income.setCategory("Income & Salary");
        income.setConfidenceSource("MANUAL");
        income.setMatchedRule("Direct Entry");
        income.setUsername("inr_user");
        income.setCreatedAt(java.time.LocalDateTime.now());
        com.financeapp.model.Income saved = incomeRepository.save(income);

        assertNotNull(saved.getId());

        // 3. Verify display conversion across all views for this income:
        // (a) Success confirmation immediately after submitting:
        var confirmDisplay = currencyConversionService.convertUsdForUser(saved.getAmount(), "inr_user");
        assertEquals("₹50,000.00", confirmDisplay.formatted());

        // (b) Recent transactions list:
        List<com.financeapp.model.Income> list = incomeRepository.findByUsernameOrderByCreatedAtDesc("inr_user");
        assertEquals(1, list.size());
        var listDisplay = currencyConversionService.convertUsdForUser(list.get(0).getAmount(), "inr_user");
        assertEquals("₹50,000.00", listDisplay.formatted());

        // (c) Transactions table:
        var tableDisplay = currencyConversionService.convertUsdForUser(list.get(0).getAmount(), "inr_user");
        assertEquals("₹50,000.00", tableDisplay.formatted());

        // (d) Dashboard total:
        BigDecimal totalIncomeUsd = list.stream().map(com.financeapp.model.Income::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        var dashboardTotalDisplay = currencyConversionService.convertUsdForUser(totalIncomeUsd, "inr_user");
        assertEquals("₹50,000.00", dashboardTotalDisplay.formatted());
    }

    @Test
    void testCurrencyPreferenceSwitchingAndDatabaseUsdIntegrity() {
        // 1. Register a user initially with preferred currency INR
        RegisterRequest registerRequest = new RegisterRequest(
                TEST_USERNAME,
                "tester@example.com",
                "password123",
                "IN",
                "INR"
        );
        authService.register(registerRequest);

        UserProfileDto profile = authService.getProfile(TEST_USERNAME);
        assertEquals("IN", profile.getCountry());
        assertEquals("INR", profile.getPreferredCurrency());

        // 2. Add an expense of $100.00 USD
        Transaction expense = new Transaction();
        expense.setAmount(new BigDecimal("100.00"));
        expense.setDescription("Team Lunch");
        expense.setPaymentMethod("Credit");
        expense.setCategory("Food & Dining");
        expense.setConfidenceSource("MANUAL");
        expense.setUsername(TEST_USERNAME);

        Transaction savedTx = transactionRepository.save(expense);
        assertNotNull(savedTx.getId());

        // 3. Confirm the underlying database row still stores exactly 100.00 in USD unchanged
        List<Transaction> dbRows = transactionRepository.findByUsernameOrderByCreatedAtDesc(TEST_USERNAME);
        assertEquals(1, dbRows.size());
        Transaction dbRow = dbRows.get(0);
        assertEquals(0, new BigDecimal("100.00").compareTo(dbRow.getAmount()), "DB amount must remain exactly 100.00 USD");

        // 4. Confirm the display layer returns the converted INR amount (100 * 83.50 = ₹8,350.00)
        var convertedInr = currencyConversionService.convertUsdForUser(dbRow.getAmount(), TEST_USERNAME);
        assertEquals(new BigDecimal("8350.00"), convertedInr.convertedAmount());
        assertEquals("INR", convertedInr.currencyCode());
        assertEquals("₹", convertedInr.currencySymbol());
        assertEquals("₹8,350.00", convertedInr.formatted());

        // 5. Switch currency preference back to USD
        UpdateProfileRequest updateRequest = new UpdateProfileRequest("US", "USD");
        UserProfileDto updatedProfile = authService.updateProfile(TEST_USERNAME, updateRequest);
        assertEquals("US", updatedProfile.getCountry());
        assertEquals("USD", updatedProfile.getPreferredCurrency());

        // 6. Confirm the display layer reverts correctly to $100.00
        var convertedUsd = currencyConversionService.convertUsdForUser(dbRow.getAmount(), TEST_USERNAME);
        assertEquals(new BigDecimal("100.00"), convertedUsd.convertedAmount());
        assertEquals("USD", convertedUsd.currencyCode());
        assertEquals("$", convertedUsd.currencySymbol());
        assertEquals("$100.00", convertedUsd.formatted());

        // 7. Verify zero data loss or drift in the underlying database row
        List<Transaction> dbRowsAfterRevert = transactionRepository.findByUsernameOrderByCreatedAtDesc(TEST_USERNAME);
        assertEquals(1, dbRowsAfterRevert.size());
        assertEquals(0, new BigDecimal("100.00").compareTo(dbRowsAfterRevert.get(0).getAmount()), "DB amount must strictly stay 100.00 USD without drift");
    }
}
