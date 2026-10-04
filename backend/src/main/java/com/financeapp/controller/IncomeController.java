package com.financeapp.controller;

import com.financeapp.dto.CategorizationResult;
import com.financeapp.dto.IncomeRequest;
import com.financeapp.dto.RecategorizeRequest;
import com.financeapp.dto.UpdateTransactionRequest;
import com.financeapp.model.Income;
import com.financeapp.repository.IncomeRepository;
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
@RequestMapping({"/api/incomes", "/api/income"})
public class IncomeController {

    @Autowired
    private IncomeRepository incomeRepository;

    @Autowired
    private CategoryService categoryService;

    @PostMapping
    public ResponseEntity<Income> addIncome(
            @Valid @RequestBody IncomeRequest request,
            Authentication authentication
    ) {
        Income income = new Income();
        income.setAmount(request.getAmount());
        income.setDescription(request.getDescription());
        income.setSource(request.getSource());
        income.setUsername(authentication.getName());

        // Run Categorization Pipeline (Rule-based -> ML Fallback)
        CategorizationResult result = categoryService.categorize(request.getDescription(), authentication.getName());
        income.setCategory(result.category());
        income.setMatchedRule(result.matchedRule());
        income.setConfidenceSource(result.confidenceSource());

        Income saved = incomeRepository.save(income);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateIncome(
            @PathVariable Long id,
            @Valid @RequestBody UpdateTransactionRequest request,
            Authentication authentication
    ) {
        Income income = incomeRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Income entry not found"));

        if (!income.getUsername().equals(authentication.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", "Forbidden: You can only edit your own income records."));
        }

        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "Amount must be greater than 0."));
        }

        if (!CategoryService.isStandardIncomeCategory(request.getCategory())) {
            return ResponseEntity.badRequest().body(Map.of("message",
                    "Invalid income category '" + request.getCategory() + "'. Allowed categories: " + CategoryService.INCOME_CATEGORIES));
        }

        if (request.getDescription() == null || request.getDescription().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Description is required."));
        }

        // Amount update: only update if modified
        if (Boolean.TRUE.equals(request.getAmountModified()) ||
                (request.getAmount() != null && income.getAmount().compareTo(request.getAmount()) != 0)) {
            income.setAmount(request.getAmount());
        }

        income.setDescription(request.getDescription().trim());

        String newCat = CategoryService.normalizeCategory(request.getCategory());
        income.setCategory(newCat);
        income.setMatchedRule("USER");
        income.setConfidenceSource("USER");
        income.setManuallyCategorized(true);

        if (request.getSource() != null && !request.getSource().isBlank()) {
            income.setSource(request.getSource().trim());
        }

        Income saved = incomeRepository.save(income);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}/category")
    public ResponseEntity<Income> recategorizeIncome(
            @PathVariable Long id,
            @Valid @RequestBody RecategorizeRequest request,
            Authentication authentication
    ) {
        Income income = incomeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Income entry not found"));

        if (!income.getUsername().equals(authentication.getName())) {
            return ResponseEntity.status(403).build();
        }

        // Validate that requested category is a valid standard category
        if (!CategoryService.isStandardCategory(request.getCategory())) {
            throw new IllegalArgumentException("Invalid category '" + request.getCategory()
                    + "'. Category must be one of the standard categories: " + CategoryService.STANDARD_CATEGORIES);
        }

        income.setCategory(CategoryService.normalizeCategory(request.getCategory()));
        income.setMatchedRule("USER");
        income.setConfidenceSource("USER");
        income.setManuallyCategorized(true);

        Income updated = incomeRepository.save(income);
        return ResponseEntity.ok(updated);
    }

    @GetMapping
    public ResponseEntity<List<Income>> getIncomes(Authentication authentication) {
        List<Income> incomes = incomeRepository
                .findByUsernameOrderByCreatedAtDesc(authentication.getName());
        return ResponseEntity.ok(incomes);
    }
}
