package com.financeapp.config;

import com.financeapp.dto.RecategorizeSummary;
import com.financeapp.model.Income;
import com.financeapp.model.Transaction;
import com.financeapp.model.User;
import com.financeapp.repository.IncomeRepository;
import com.financeapp.repository.TransactionRepository;
import com.financeapp.repository.UserRepository;
import com.financeapp.service.CategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Order(2)
public class UserDataInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(UserDataInitializer.class);

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final IncomeRepository incomeRepository;
    private final CategoryService categoryService;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    public UserDataInitializer(UserRepository userRepository,
                               TransactionRepository transactionRepository,
                               IncomeRepository incomeRepository,
                               CategoryService categoryService,
                               org.springframework.security.crypto.password.PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.incomeRepository = incomeRepository;
        this.categoryService = categoryService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedAndCategorizeTestUser();
    }

    public void seedAndCategorizeTestUser() {
        String username = "pradeep";

        // 1. Ensure user exists and has valid credentials
        userRepository.findByUsername(username).ifPresentOrElse(
                user -> {
                    if (!passwordEncoder.matches("password123", user.getPassword())) {
                        user.setPassword(passwordEncoder.encode("password123"));
                        userRepository.save(user);
                        logger.info("Updated password hash for user '{}'", username);
                    }
                },
                () -> {
                    User user = new User(
                            username,
                            "789@gmail.com",
                            passwordEncoder.encode("password123"),
                            "IN",
                            "INR"
                    );
                    userRepository.save(user);
                    logger.info("Initialized user '{}'", username);
                }
        );

        // 2. Ensure income exists
        List<Income> incomes = incomeRepository.findByUsernameOrderByCreatedAtDesc(username);
        if (incomes.isEmpty()) {
            Income inc = new Income(BigDecimal.valueOf(598.8024), "salary", "Salary", username);
            inc.setCategory("Income & Salary");
            inc.setMatchedRule("Keyword: salary");
            inc.setConfidenceSource("RULE");
            incomeRepository.save(inc);
            logger.info("Initialized salary income for '{}'", username);
        }

        // 3. Ensure transactions exist
        List<Transaction> existing = transactionRepository.findByUsernameOrderByCreatedAtDesc(username);
        if (existing.isEmpty()) {
            // Seed baseline: 4 categorized initially (grocery, food, rent, internet) = 24.6% categorized, 75.4% Uncategorized
            createTx(BigDecimal.valueOf(29.9401), "grocery", "UPI", "Food & Dining", "Keyword: grocery", "RULE", false, LocalDateTime.now().minusDays(5), username);
            createTx(BigDecimal.valueOf(4.7904), "food", "Cash", "Food & Dining", "Keyword: food", "RULE", false, LocalDateTime.now().minusDays(5), username);
            createTx(BigDecimal.valueOf(8.9820), "movie", "UPI", "Uncategorized", null, "UNASSIGNED", false, LocalDateTime.now().minusDays(4), username);
            createTx(BigDecimal.valueOf(95.8084), "rent", "Cash", "Utilities & Bills", "Keyword: rent", "RULE", false, LocalDateTime.now().minusDays(4), username);
            createTx(BigDecimal.valueOf(5.9880), "current", "UPI", "Uncategorized", null, "UNASSIGNED", false, LocalDateTime.now().minusDays(3), username);
            createTx(BigDecimal.valueOf(59.8802), "shopping", "UPI", "Uncategorized", null, "UNASSIGNED", false, LocalDateTime.now().minusDays(3), username);
            createTx(BigDecimal.valueOf(119.7605), "investment", "Cash", "Uncategorized", null, "UNASSIGNED", false, LocalDateTime.now().minusDays(2), username);
            createTx(BigDecimal.valueOf(107.7844), "savings", "Cash", "Uncategorized", null, "UNASSIGNED", false, LocalDateTime.now().minusDays(2), username);
            createTx(BigDecimal.valueOf(11.9760), "shopping", "UPI", "Uncategorized", null, "UNASSIGNED", false, LocalDateTime.now().minusDays(1), username);
            createTx(BigDecimal.valueOf(59.8802), "travel", "Cash", "Uncategorized", null, "UNASSIGNED", false, LocalDateTime.now().minusDays(1), username);
            createTx(BigDecimal.valueOf(59.8802), "friend expenses", "Cash", "Uncategorized", null, "UNASSIGNED", false, LocalDateTime.now(), username);
            createTx(BigDecimal.valueOf(10.7784), "internet", "UPI", "Utilities & Bills", "Keyword: internet", "RULE", false, LocalDateTime.now(), username);
            logger.info("Initialized 12 baseline transactions for '{}'", username);
        } else {
            // Update transaction #7 to "investment" if it was "house"
            for (Transaction t : existing) {
                if (t.getId() != null && t.getId() == 7L) {
                    t.setDescription("investment");
                    t.setCategory("Uncategorized");
                    t.setMatchedRule(null);
                    t.setConfidenceSource("UNASSIGNED");
                    t.setManuallyCategorized(false);
                    transactionRepository.save(t);
                }
            }
        }

        // 4. Sanitize any non-standard categories
        List<Transaction> sanitized = categoryService.sanitizeNonStandardCategories(username);
        if (!sanitized.isEmpty()) {
            logger.info("Sanitized {} transactions with non-standard categories for '{}'", sanitized.size(), username);
        }

        // 5. Run retroactive categorization across all users
        RecategorizeSummary summary = categoryService.recategorizeAllUsersUncategorized();
        logger.info("Retroactive recategorization across all users: processed={}, matched={}, unmatched={}",
                summary.totalUncategorizedProcessed(), summary.matchedCount(), summary.unmatchedCount());
    }

    private void createTx(BigDecimal amount, String desc, String method, String cat, String rule, String src, boolean manual, LocalDateTime date, String username) {
        Transaction t = new Transaction(amount, desc, method, username);
        t.setCategory(cat);
        t.setMatchedRule(rule);
        t.setConfidenceSource(src);
        t.setManuallyCategorized(manual);
        transactionRepository.save(t);
    }
}
