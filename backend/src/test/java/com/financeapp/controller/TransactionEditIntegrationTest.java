package com.financeapp.controller;

import com.financeapp.config.CurrencyDataInitializer;
import com.financeapp.dto.UpdateTransactionRequest;
import com.financeapp.model.Income;
import com.financeapp.model.Transaction;
import com.financeapp.model.User;
import com.financeapp.repository.IncomeRepository;
import com.financeapp.repository.TransactionRepository;
import com.financeapp.repository.UserRepository;
import com.financeapp.service.CategoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.annotation.DirtiesContext;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class TransactionEditIntegrationTest {

    @Autowired
    private ExpenseController expenseController;

    @Autowired
    private IncomeController incomeController;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private IncomeRepository incomeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CurrencyDataInitializer currencyDataInitializer;

    private Authentication userAuth;
    private Authentication otherAuth;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        incomeRepository.deleteAll();
        userRepository.deleteAll();
        currencyDataInitializer.seedInitialCurrencies();

        User user = new User("alice", "alice@example.com", "pass", "IN", "INR");
        userRepository.save(user);

        User other = new User("bob", "bob@example.com", "pass", "US", "USD");
        userRepository.save(other);

        userAuth = new UsernamePasswordAuthenticationToken("alice", "pass", Collections.emptyList());
        otherAuth = new UsernamePasswordAuthenticationToken("bob", "pass", Collections.emptyList());
    }

    @Test
    @DisplayName("Expense edit updates category, amount, description and preserves timestamp")
    void testExpenseEdit() {
        Transaction tx = new Transaction(new BigDecimal("10.00000000"), "Initial Item", "UPI", "alice");
        tx.setCategory("Uncategorized");
        tx.setConfidenceSource("UNASSIGNED");
        tx.setMatchedRule(null);
        tx.setManuallyCategorized(false);
        Transaction saved = transactionRepository.save(tx);
        Transaction storedBeforeEdit = transactionRepository.findById(saved.getId()).orElseThrow();
        LocalDateTime initialCreatedAt = storedBeforeEdit.getCreatedAt();
        assertNotNull(initialCreatedAt);

        UpdateTransactionRequest request = new UpdateTransactionRequest();
        request.setDescription("Corrected Grocery");
        request.setCategory("Food & Dining");
        request.setAmount(new BigDecimal("15.50000000"));
        request.setPaymentMethod("Cash");
        request.setAmountModified(true);

        ResponseEntity<?> response = expenseController.updateExpense(saved.getId(), request, userAuth);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        Transaction updated = transactionRepository.findById(saved.getId()).orElseThrow();
        assertEquals("Corrected Grocery", updated.getDescription());
        assertEquals("Food & Dining", updated.getCategory());
        assertEquals(0, new BigDecimal("15.50000000").compareTo(updated.getAmount()));
        assertEquals("USER", updated.getConfidenceSource());
        assertEquals("USER", updated.getMatchedRule());
        assertTrue(updated.isManuallyCategorized());
        assertEquals(initialCreatedAt, updated.getCreatedAt(), "Original timestamp must be strictly preserved");
    }

    @Test
    @DisplayName("Editing expense by a different user returns 403 Forbidden")
    void testExpenseEditForbiddenForOtherUser() {
        Transaction tx = new Transaction(new BigDecimal("10.00000000"), "Alice Expense", "UPI", "alice");
        tx.setCategory("Food & Dining");
        tx.setConfidenceSource("RULE");
        Transaction saved = transactionRepository.save(tx);

        UpdateTransactionRequest request = new UpdateTransactionRequest();
        request.setDescription("Hacked Description");
        request.setCategory("Food & Dining");
        request.setAmount(new BigDecimal("10.00000000"));

        ResponseEntity<?> response = expenseController.updateExpense(saved.getId(), request, otherAuth);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());

        Transaction unchanged = transactionRepository.findById(saved.getId()).orElseThrow();
        assertEquals("Alice Expense", unchanged.getDescription());
    }

    @Test
    @DisplayName("Editing expense with invalid category returns 400 Bad Request")
    void testExpenseEditInvalidCategory() {
        Transaction tx = new Transaction(new BigDecimal("10.00000000"), "Item", "UPI", "alice");
        tx.setCategory("Food & Dining");
        tx.setConfidenceSource("RULE");
        Transaction saved = transactionRepository.save(tx);

        UpdateTransactionRequest request = new UpdateTransactionRequest();
        request.setDescription("Item");
        request.setCategory("InvalidRandomCategory");
        request.setAmount(new BigDecimal("10.00000000"));

        ResponseEntity<?> response = expenseController.updateExpense(saved.getId(), request, userAuth);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    @DisplayName("Income edit updates amount with high precision, preserves timestamp, and trains ML")
    void testIncomeEditWithHighPrecision() {
        // Initial income: 50000 INR -> 598.80239521 USD
        BigDecimal initialUsd = new BigDecimal("50000").divide(new BigDecimal("83.50"), 8, RoundingMode.HALF_UP);
        Income inc = new Income(initialUsd, "Initial Freelance", "Freelance", "alice");
        inc.setCategory("Freelance & Business");
        inc.setConfidenceSource("RULE");
        Income saved = incomeRepository.save(inc);
        Income storedBeforeEdit = incomeRepository.findById(saved.getId()).orElseThrow();
        LocalDateTime initialCreatedAt = storedBeforeEdit.getCreatedAt();
        assertNotNull(initialCreatedAt);

        // Edit category only: amountModified = false
        UpdateTransactionRequest editCatReq = new UpdateTransactionRequest();
        editCatReq.setDescription("Initial Freelance");
        editCatReq.setCategory("Income & Salary");
        editCatReq.setAmount(initialUsd);
        editCatReq.setAmountModified(false);

        ResponseEntity<?> resp1 = incomeController.updateIncome(saved.getId(), editCatReq, userAuth);
        assertEquals(HttpStatus.OK, resp1.getStatusCode());

        Income catUpdated = incomeRepository.findById(saved.getId()).orElseThrow();
        assertEquals("Income & Salary", catUpdated.getCategory());
        assertEquals(0, initialUsd.compareTo(catUpdated.getAmount()), "Stored USD amount must remain unchanged");
        assertEquals("USER", catUpdated.getConfidenceSource());
        assertEquals("USER", catUpdated.getMatchedRule());
        assertTrue(catUpdated.isManuallyCategorized());
        assertEquals(initialCreatedAt, catUpdated.getCreatedAt());

        // Edit amount: 75000 INR -> 898.20359281 USD (8 decimal places)
        BigDecimal newUsd = new BigDecimal("75000").divide(new BigDecimal("83.50"), 8, RoundingMode.HALF_UP);
        UpdateTransactionRequest editAmtReq = new UpdateTransactionRequest();
        editAmtReq.setDescription("Initial Freelance");
        editAmtReq.setCategory("Income & Salary");
        editAmtReq.setAmount(newUsd);
        editAmtReq.setAmountModified(true);

        ResponseEntity<?> resp2 = incomeController.updateIncome(saved.getId(), editAmtReq, userAuth);
        assertEquals(HttpStatus.OK, resp2.getStatusCode());

        Income amtUpdated = incomeRepository.findById(saved.getId()).orElseThrow();
        assertEquals(0, newUsd.compareTo(amtUpdated.getAmount()), "Stored value must match 75000 / 83.5 at full precision");
        assertEquals(initialCreatedAt, amtUpdated.getCreatedAt(), "Original timestamp must be unchanged");
    }

    @Test
    @DisplayName("Editing income by a different user returns 403 Forbidden")
    void testIncomeEditForbiddenForOtherUser() {
        Income inc = new Income(new BigDecimal("100.00"), "Alice Income", "Salary", "alice");
        inc.setCategory("Income & Salary");
        inc.setConfidenceSource("RULE");
        Income saved = incomeRepository.save(inc);

        UpdateTransactionRequest request = new UpdateTransactionRequest();
        request.setDescription("Hacked Income");
        request.setCategory("Income & Salary");
        request.setAmount(new BigDecimal("200.00"));

        ResponseEntity<?> response = incomeController.updateIncome(saved.getId(), request, otherAuth);
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }
}
