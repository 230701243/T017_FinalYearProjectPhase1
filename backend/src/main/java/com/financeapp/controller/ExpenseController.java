package com.financeapp.controller;

import com.financeapp.dto.CategorizationResult;
import com.financeapp.dto.ExpenseRequest;
import com.financeapp.dto.RecategorizeRequest;
import com.financeapp.dto.RecategorizeSummary;
import com.financeapp.dto.UpdateTransactionRequest;
import com.financeapp.model.Transaction;
import com.financeapp.repository.TransactionRepository;
import com.financeapp.service.CategoryService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/expenses", "/api/transactions"})
public class ExpenseController {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private CategoryService categoryService;

    @PostMapping
    public ResponseEntity<Transaction> addExpense(
            @Valid @RequestBody ExpenseRequest request,
            Authentication authentication
    ) {
        Transaction transaction = new Transaction();
        transaction.setAmount(request.getAmount());
        transaction.setDescription(request.getDescription());
        transaction.setPaymentMethod(request.getPaymentMethod());
        transaction.setUsername(authentication.getName());

        // Run Categorization Pipeline (Rule-based -> ML Fallback)
        CategorizationResult result = categoryService.categorize(request.getDescription(), authentication.getName());
        transaction.setCategory(result.category());
        transaction.setMatchedRule(result.matchedRule());
        transaction.setConfidenceSource(result.confidenceSource());

        Transaction saved = transactionRepository.save(transaction);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateExpense(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTransactionRequest request,
            Authentication authentication
    ) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        if (!transaction.getUsername().equals(authentication.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Forbidden: You can only edit your own transactions."));
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Amount must be greater than 0."));
        }

        if (!CategoryService.isStandardExpenseCategory(request.getCategory())) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    "Invalid expense category '" + request.getCategory() + "'. Allowed categories: " + CategoryService.EXPENSE_CATEGORIES));
        }

        if (request.getDescription() == null || request.getDescription().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Description is required."));
        }

        // Amount update: only update if modified
        if (Boolean.TRUE.equals(request.getAmountModified()) ||
                (request.getAmount() != null && transaction.getAmount().compareTo(request.getAmount()) != 0)) {
            transaction.setAmount(request.getAmount());
        }

        transaction.setDescription(request.getDescription().trim());

        String newCat = CategoryService.normalizeCategory(request.getCategory());
        transaction.setCategory(newCat);
        transaction.setMatchedRule("USER");
        transaction.setConfidenceSource("USER");
        transaction.setManuallyCategorized(true);

        if (request.getPaymentMethod() != null && !request.getPaymentMethod().isBlank()) {
            transaction.setPaymentMethod(request.getPaymentMethod().trim());
        }

        Transaction saved = transactionRepository.save(transaction);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}/category")
    public ResponseEntity<Transaction> recategorizeExpense(
            @PathVariable Long id,
            @Valid @RequestBody RecategorizeRequest request,
            Authentication authentication
    ) {
        Transaction transaction = transactionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transaction not found"));

        if (!transaction.getUsername().equals(authentication.getName())) {
            return ResponseEntity.status(403).build();
        }

        // Validate that requested category is a valid standard category
        if (!CategoryService.isStandardCategory(request.getCategory())) {
            throw new IllegalArgumentException("Invalid category '" + request.getCategory()
                    + "'. Category must be one of the standard categories: " + CategoryService.STANDARD_CATEGORIES);
        }

        transaction.setCategory(CategoryService.normalizeCategory(request.getCategory()));
        transaction.setMatchedRule("USER");
        transaction.setConfidenceSource("USER");
        transaction.setManuallyCategorized(true);

        Transaction updated = transactionRepository.save(transaction);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/recategorize-uncategorized")
    public ResponseEntity<RecategorizeSummary> recategorizeUncategorized(Authentication authentication) {
        RecategorizeSummary summary = categoryService.recategorizeUncategorized(authentication.getName());
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/recategorize-all-uncategorized")
    public ResponseEntity<RecategorizeSummary> recategorizeAllUncategorized() {
        RecategorizeSummary summary = categoryService.recategorizeAllUsersUncategorized();
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/cleanup-non-standard-categories")
    public ResponseEntity<List<Transaction>> cleanupNonStandardCategories(Authentication authentication) {
        List<Transaction> cleaned = categoryService.sanitizeNonStandardCategories(authentication.getName());
        return ResponseEntity.ok(cleaned);
    }

    @GetMapping
    public ResponseEntity<List<Transaction>> getExpenses(Authentication authentication) {
        List<Transaction> expenses = transactionRepository
                .findByUsernameOrderByCreatedAtDesc(authentication.getName());
        return ResponseEntity.ok(expenses);
    }
}
