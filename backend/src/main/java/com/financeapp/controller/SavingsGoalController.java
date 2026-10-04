package com.financeapp.controller;

import com.financeapp.dto.GoalRequest;
import com.financeapp.dto.GoalResponse;
import com.financeapp.dto.LocalizedMessage;
import java.util.Map;
import com.financeapp.model.Income;
import com.financeapp.model.SavingsGoal;
import com.financeapp.model.Transaction;
import com.financeapp.repository.IncomeRepository;
import com.financeapp.repository.SavingsGoalRepository;
import com.financeapp.repository.TransactionRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/goals")
public class SavingsGoalController {

    @Autowired
    private SavingsGoalRepository savingsGoalRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private IncomeRepository incomeRepository;

    @Autowired(required = false)
    private com.financeapp.repository.UserRepository userRepository;

    @Autowired(required = false)
    private com.financeapp.service.CurrencyConversionService currencyConversionService;

    private String getPreferredCurrency(String username) {
        if (username != null && userRepository != null) {
            return userRepository.findByUsername(username)
                    .map(com.financeapp.model.User::getPreferredCurrency)
                    .filter(c -> !c.isBlank())
                    .orElse("USD");
        }
        return "USD";
    }

    @PostMapping
    public ResponseEntity<GoalResponse> createGoal(
            @Valid @RequestBody GoalRequest request,
            Authentication authentication
    ) {
        String username = authentication.getName();
        SavingsGoal goal = new SavingsGoal();
        goal.setName(request.getName());
        goal.setTargetAmount(request.getTargetAmount());
        goal.setCurrentAmount(request.getCurrentAmount());
        goal.setTargetDate(request.getTargetDate());
        goal.setUsername(username);

        SavingsGoal saved = savingsGoalRepository.save(goal);
        return ResponseEntity.ok(mapToResponse(saved, username));
    }

    @GetMapping
    public ResponseEntity<List<GoalResponse>> getGoals(Authentication authentication) {
        String username = authentication.getName();
        List<SavingsGoal> goals = savingsGoalRepository.findByUsernameOrderByCreatedAtDesc(username);
        
        List<GoalResponse> responses = new ArrayList<>();
        for (SavingsGoal g : goals) {
            responses.add(mapToResponse(g, username));
        }
        return ResponseEntity.ok(responses);
    }

    @PutMapping("/{id}")
    public ResponseEntity<GoalResponse> updateGoal(
            @PathVariable Long id,
            @Valid @RequestBody GoalRequest request,
            Authentication authentication
    ) {
        String username = authentication.getName();
        SavingsGoal goal = savingsGoalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Savings goal not found"));

        if (!goal.getUsername().equals(username)) {
            return ResponseEntity.status(403).build();
        }

        goal.setName(request.getName());
        goal.setTargetAmount(request.getTargetAmount());
        goal.setCurrentAmount(request.getCurrentAmount());

        SavingsGoal updated = savingsGoalRepository.save(goal);
        return ResponseEntity.ok(mapToResponse(updated, username));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGoal(
            @PathVariable Long id,
            Authentication authentication
    ) {
        SavingsGoal goal = savingsGoalRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Savings goal not found"));

        if (!goal.getUsername().equals(authentication.getName())) {
            return ResponseEntity.status(403).build();
        }

        savingsGoalRepository.delete(goal);
        return ResponseEntity.noContent().build();
    }

    private GoalResponse mapToResponse(SavingsGoal goal, String username) {
        BigDecimal target = goal.getTargetAmount();
        BigDecimal current = goal.getCurrentAmount();
        BigDecimal remaining = target.subtract(current);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;

        double progressPct = 0.0;
        if (target.compareTo(BigDecimal.ZERO) > 0) {
            progressPct = current.divide(target, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
            if (progressPct > 100.0) progressPct = 100.0;
        }

        // Calculate average monthly savings rate
        LocalDateTime now = LocalDateTime.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();

        List<Income> incomes = incomeRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Transaction> expenses = transactionRepository.findByUsernameOrderByCreatedAtDesc(username);

        BigDecimal monthlyIncome = incomes.stream()
                .filter(i -> i.getCreatedAt() != null &&
                             i.getCreatedAt().getMonthValue() == currentMonth &&
                             i.getCreatedAt().getYear() == currentYear)
                .map(Income::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyExpenses = expenses.stream()
                .filter(e -> e.getCreatedAt() != null &&
                             e.getCreatedAt().getMonthValue() == currentMonth &&
                             e.getCreatedAt().getYear() == currentYear)
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlySavingsRate = monthlyIncome.subtract(monthlyExpenses);

        String preferredCurrency = getPreferredCurrency(username);
        String zeroMoney = (currencyConversionService != null)
                ? currencyConversionService.formatAmount(BigDecimal.ZERO, preferredCurrency)
                : com.financeapp.service.CurrencyConversionService.formatMoney(BigDecimal.ZERO, preferredCurrency);

        double estMonths = -1;
        String estCompletionDate = "Indefinite (Monthly Savings <= " + zeroMoney + ")";
        LocalizedMessage estCompletionMsg;

        if (current.compareTo(target) >= 0) {
            estMonths = 0;
            estCompletionDate = "Goal Completed! 🎉";
            estCompletionMsg = new LocalizedMessage("goal.status.completed");
        } else if (monthlySavingsRate.compareTo(BigDecimal.ZERO) > 0) {
            estMonths = remaining.divide(monthlySavingsRate, 2, RoundingMode.HALF_UP).doubleValue();
            long monthsToAdd = (long) Math.ceil(estMonths);
            YearMonth completionYM = YearMonth.now().plusMonths(monthsToAdd);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
            estCompletionDate = String.format("Est. %s (~%.1f mos)", completionYM.format(formatter), estMonths);
            estCompletionMsg = new LocalizedMessage("goal.status.estimated", Map.of(
                    "date", completionYM.format(formatter),
                    "months", Math.round(estMonths * 10.0) / 10.0
            ));
        } else {
            estCompletionMsg = new LocalizedMessage("goal.status.indefinite", Map.of(
                    "zero", (currencyConversionService != null)
                            ? currencyConversionService.toMoneyParam(BigDecimal.ZERO, preferredCurrency)
                            : com.financeapp.service.CurrencyConversionService.toMoney(BigDecimal.ZERO, preferredCurrency)
            ));
        }

        return new GoalResponse(
                goal.getId(),
                goal.getName(),
                target,
                current,
                remaining,
                Math.round(progressPct * 10.0) / 10.0,
                Math.round(estMonths * 10.0) / 10.0,
                estCompletionDate,
                goal.getTargetDate(),
                goal.getCreatedAt(),
                estCompletionMsg
        );
    }
}
