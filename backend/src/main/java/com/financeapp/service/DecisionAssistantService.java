package com.financeapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.financeapp.dto.*;
import com.financeapp.model.Income;
import com.financeapp.model.SavingsGoal;
import com.financeapp.model.Subscription;
import com.financeapp.model.Transaction;
import com.financeapp.model.User;
import com.financeapp.repository.CurrencyConfigRepository;
import com.financeapp.repository.IncomeRepository;
import com.financeapp.repository.SavingsGoalRepository;
import com.financeapp.repository.SubscriptionRepository;
import com.financeapp.repository.TransactionRepository;
import com.financeapp.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DecisionAssistantService {

    private static final Logger log = LoggerFactory.getLogger(DecisionAssistantService.class);

    public static final String SYSTEM_PROMPT =
            "You are a financial assistant answering general questions about the user's finances using only the raw data provided (balance, income, expenses, savings, goals, recent transactions). " +
            "You do not have access to purchase scenario comparisons (buy now/wait/EMI) — if the user asks a purchase-affordability question, answer using only current balance, income, expenses, and goal status, and note that you are giving a general assessment, not a detailed scenario comparison.";

    public static String buildSystemPrompt(String currencyCode, String currencySymbol) {
        return buildSystemPrompt(currencyCode, currencySymbol, "English");
    }

    public static String buildSystemPrompt(String currencyCode, String currencySymbol, String languageName) {
        String code = (currencyCode != null && !currencyCode.isBlank()) ? currencyCode.trim().toUpperCase() : "USD";
        String symbol = (currencySymbol != null && !currencySymbol.isBlank()) ? currencySymbol : "$";
        String lang = (languageName != null && !languageName.isBlank()) ? languageName.trim() : "English";
        return SYSTEM_PROMPT + String.format(
                " All monetary values are in %s (%s). Always write amounts using %s. Never use $ or USD unless the currency is USD." +
                " Respond in %s. Write all numbers with Western digits exactly as provided. Keep the currency symbol as given. Translate the closing line that this is not professional financial advice.",
                code, symbol, symbol, lang
        );
    }

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private IncomeRepository incomeRepository;

    @Autowired(required = false)
    private SubscriptionRepository subscriptionRepository;

    @Autowired(required = false)
    private SavingsGoalRepository savingsGoalRepository;

    @Autowired
    private RecommendationService recommendationService;

    @Autowired
    private LlmService llmService;

    @Autowired(required = false)
    private CurrencyConversionService currencyConversionService;

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired(required = false)
    private CurrencyConfigRepository currencyConfigRepository;

    private final ObjectMapper objectMapper;

    public DecisionAssistantService() {
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public DecisionAssistantService(
            TransactionRepository transactionRepository,
            IncomeRepository incomeRepository,
            SubscriptionRepository subscriptionRepository,
            SavingsGoalRepository savingsGoalRepository,
            RecommendationService recommendationService,
            LlmService llmService,
            ObjectMapper objectMapper
    ) {
        this(transactionRepository, incomeRepository, subscriptionRepository, savingsGoalRepository,
                recommendationService, llmService, objectMapper, null, null, null);
    }

    public DecisionAssistantService(
            TransactionRepository transactionRepository,
            IncomeRepository incomeRepository,
            SubscriptionRepository subscriptionRepository,
            SavingsGoalRepository savingsGoalRepository,
            RecommendationService recommendationService,
            LlmService llmService,
            ObjectMapper objectMapper,
            CurrencyConversionService currencyConversionService,
            UserRepository userRepository,
            CurrencyConfigRepository currencyConfigRepository
    ) {
        this.transactionRepository = transactionRepository;
        this.incomeRepository = incomeRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.savingsGoalRepository = savingsGoalRepository;
        this.recommendationService = recommendationService;
        this.llmService = llmService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().registerModule(new JavaTimeModule());
        this.currencyConversionService = currencyConversionService;
        this.userRepository = userRepository;
        this.currencyConfigRepository = currencyConfigRepository;
    }

    public void setCurrencyConversionService(CurrencyConversionService currencyConversionService) {
        this.currencyConversionService = currencyConversionService;
    }

    public void setUserRepository(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void setCurrencyConfigRepository(CurrencyConfigRepository currencyConfigRepository) {
        this.currencyConfigRepository = currencyConfigRepository;
    }

    public String resolvePreferredCurrency(String username) {
        if (username != null && !username.isBlank()) {
            if (userRepository != null) {
                try {
                    User user = userRepository.findByUsername(username).orElse(null);
                    if (user != null && user.getPreferredCurrency() != null && !user.getPreferredCurrency().isBlank()) {
                        return user.getPreferredCurrency().trim().toUpperCase();
                    }
                } catch (Exception ignored) {}
            }
            if (currencyConversionService != null) {
                try {
                    var display = currencyConversionService.convertUsdForUser(BigDecimal.ZERO, username);
                    if (display != null && display.currencyCode() != null && !display.currencyCode().isBlank()) {
                        return display.currencyCode().trim().toUpperCase();
                    }
                } catch (Exception ignored) {}
            }
        }
        return "USD";
    }

    public String resolveCurrencySymbol(String currencyCode) {
        if (currencyConfigRepository != null) {
            try {
                var config = currencyConfigRepository.findByCurrencyCodeIgnoreCase(currencyCode).orElse(null);
                if (config != null && config.getCurrencySymbol() != null && !config.getCurrencySymbol().isBlank()) {
                    return config.getCurrencySymbol();
                }
            } catch (Exception ignored) {}
        }
        if (currencyConversionService != null) {
            try {
                var display = currencyConversionService.convertUsd(BigDecimal.ZERO, currencyCode);
                if (display != null && display.currencySymbol() != null && !display.currencySymbol().isBlank()) {
                    return display.currencySymbol();
                }
            } catch (Exception ignored) {}
        }
        if ("INR".equalsIgnoreCase(currencyCode)) return "₹";
        if ("EUR".equalsIgnoreCase(currencyCode)) return "€";
        if ("GBP".equalsIgnoreCase(currencyCode)) return "£";
        if ("JPY".equalsIgnoreCase(currencyCode)) return "¥";
        return "$";
    }

    public static String resolveLanguageName(String code) {
        if (code == null || code.isBlank()) return "English";
        return switch (code.trim().toLowerCase()) {
            case "ta" -> "Tamil";
            case "hi" -> "Hindi";
            case "ar" -> "Arabic";
            case "ja" -> "Japanese";
            case "zh", "zh-cn" -> "Chinese (Simplified)";
            case "es" -> "Spanish";
            case "bn" -> "Bengali";
            case "te" -> "Telugu";
            case "mr" -> "Marathi";
            case "gu" -> "Gujarati";
            case "kn" -> "Kannada";
            case "ml" -> "Malayalam";
            case "pa" -> "Punjabi";
            case "ur" -> "Urdu";
            case "fr" -> "French";
            case "de" -> "German";
            case "it" -> "Italian";
            case "pt" -> "Portuguese";
            case "ru" -> "Russian";
            case "ko" -> "Korean";
            case "id" -> "Indonesian";
            case "tr" -> "Turkish";
            case "vi" -> "Vietnamese";
            case "th" -> "Thai";
            default -> "English";
        };
    }

    public BigDecimal convertMonetary(BigDecimal usdAmount, String currencyCode) {
        if (usdAmount == null) {
            return BigDecimal.ZERO;
        }
        if (currencyConversionService != null) {
            try {
                var display = currencyConversionService.convertUsd(usdAmount, currencyCode);
                if (display != null && display.convertedAmount() != null) {
                    return display.convertedAmount();
                }
            } catch (Exception ignored) {}
        }
        return usdAmount.setScale(2, RoundingMode.HALF_UP);
    }

    public DecisionAssistantResponse assembleContext(String username, DecisionAssistantRequest request) {
        // 0. Confirm & load the logged-in user's preferred currency (code & symbol) from profile
        String preferredCurrency = resolvePreferredCurrency(username);
        String currencySymbol = resolveCurrencySymbol(preferredCurrency);

        List<Transaction> expenses = transactionRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Income> incomes = incomeRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Subscription> subs = subscriptionRepository != null ? subscriptionRepository.findByUsernameAndIsActiveTrue(username) : Collections.emptyList();
        List<SavingsGoal> goals = savingsGoalRepository != null ? savingsGoalRepository.findByUsernameOrderByCreatedAtDesc(username) : Collections.emptyList();

        // 1. Raw Financial Snapshot (calculations done in USD)
        DecisionAssistantResponse.FinancialSnapshot snapshot = buildFinancialSnapshot(expenses, incomes, subs);

        // 2. Active Savings Goals (with target, current, and deadline/estimated completion)
        List<GoalResponse> activeGoals = new ArrayList<>();
        for (SavingsGoal g : goals) {
            activeGoals.add(mapGoalToResponse(g, incomes, expenses, preferredCurrency));
        }

        // 3. Recent Transaction History
        List<DecisionAssistantResponse.RecentTransactionSummary> recentTransactions = new ArrayList<>();
        int maxItems = 10;
        for (int i = 0; i < Math.min(maxItems, expenses.size()); i++) {
            Transaction t = expenses.get(i);
            recentTransactions.add(new DecisionAssistantResponse.RecentTransactionSummary(
                    t.getId(),
                    "EXPENSE",
                    t.getDescription(),
                    t.getAmount(),
                    t.getCategory(),
                    t.getCreatedAt() != null ? t.getCreatedAt().toString() : null
            ));
        }
        for (int i = 0; i < Math.min(maxItems, incomes.size()); i++) {
            Income inc = incomes.get(i);
            recentTransactions.add(new DecisionAssistantResponse.RecentTransactionSummary(
                    inc.getId(),
                    "INCOME",
                    inc.getDescription(),
                    inc.getAmount(),
                    inc.getCategory(),
                    inc.getCreatedAt() != null ? inc.getCreatedAt().toString() : null
            ));
        }
        recentTransactions.sort((a, b) -> {
            if (a.date() == null && b.date() == null) return 0;
            if (a.date() == null) return 1;
            if (b.date() == null) return -1;
            return b.date().compareTo(a.date());
        });

        DecisionAssistantRequest.HypotheticalPurchase purchase = request != null ? request.getHypotheticalPurchase() : null;

        // 4. Assemble payload for LLM with converted monetary values and explicit currency fields
        String userQuestion = request != null && request.getQuestion() != null && !request.getQuestion().trim().isEmpty()
                ? request.getQuestion().trim()
                : "What is your financial assessment of my current situation?";

        String languageCode = request != null ? request.getLanguageCode() : null;
        String languageName = request != null ? request.getLanguageName() : null;

        if (languageName == null || languageName.isBlank()) {
            if (userRepository != null && username != null) {
                User u = userRepository.findByUsername(username).orElse(null);
                if (u != null && u.getPreferredLanguage() != null && !u.getPreferredLanguage().isBlank()) {
                    languageCode = u.getPreferredLanguage();
                }
            }
            languageName = resolveLanguageName(languageCode);
        }

        String llmRecommendation = null;
        if (llmService != null) {
            // Convert goal amounts to preferred currency for LLM payload
            List<GoalResponse> convertedGoals = new ArrayList<>();
            for (GoalResponse g : activeGoals) {
                convertedGoals.add(new GoalResponse(
                        g.id(),
                        g.name(),
                        convertMonetary(g.targetAmount(), preferredCurrency),
                        convertMonetary(g.currentAmount(), preferredCurrency),
                        convertMonetary(g.remainingAmount(), preferredCurrency),
                        g.progressPercentage(),
                        g.estimatedMonthsRemaining(),
                        g.estimatedCompletionDate(),
                        g.createdAt()
                ));
            }

            // Convert recent transaction amounts to preferred currency for LLM payload
            List<DecisionAssistantResponse.RecentTransactionSummary> convertedTransactions = new ArrayList<>();
            for (DecisionAssistantResponse.RecentTransactionSummary t : recentTransactions) {
                convertedTransactions.add(new DecisionAssistantResponse.RecentTransactionSummary(
                        t.id(),
                        t.type(),
                        t.description(),
                        convertMonetary(t.amount(), preferredCurrency),
                        t.category(),
                        t.date()
                ));
            }

            // Convert any hypothetical purchase amounts to preferred currency for LLM payload
            Map<String, Object> convertedPurchase = null;
            if (purchase != null) {
                convertedPurchase = new LinkedHashMap<>();
                if (purchase.getAmount() != null) {
                    convertedPurchase.put("amount", convertMonetary(purchase.getAmount(), preferredCurrency));
                }
                if (purchase.getDescription() != null) {
                    convertedPurchase.put("description", purchase.getDescription());
                }
                if (purchase.getTiming() != null) {
                    convertedPurchase.put("timing", purchase.getTiming());
                }
                if (purchase.getEmiMonthlyAmount() != null) {
                    convertedPurchase.put("emiMonthlyAmount", convertMonetary(purchase.getEmiMonthlyAmount(), preferredCurrency));
                }
                if (purchase.getEmiTenureMonths() != null) {
                    convertedPurchase.put("emiTenureMonths", purchase.getEmiTenureMonths());
                }
                if (purchase.getDelayMonths() != null) {
                    convertedPurchase.put("delayMonths", purchase.getDelayMonths());
                }
            }

            Map<String, Object> llmPayload = new LinkedHashMap<>();
            llmPayload.put("userQuestion", userQuestion);
            llmPayload.put("languageCode", languageCode != null ? languageCode : "en");
            llmPayload.put("languageName", languageName);
            llmPayload.put("currencyCode", preferredCurrency);
            llmPayload.put("currencySymbol", currencySymbol);
            llmPayload.put("currentBalance", convertMonetary(snapshot.totalBalance(), preferredCurrency));
            llmPayload.put("monthlyIncome", convertMonetary(snapshot.monthlyIncome(), preferredCurrency));
            llmPayload.put("monthlyExpenses", convertMonetary(snapshot.monthlyExpenses(), preferredCurrency));
            llmPayload.put("currentSavings", convertMonetary(snapshot.monthlySavings(), preferredCurrency));
            llmPayload.put("activeSavingsGoals", convertedGoals);
            llmPayload.put("recentTransactions", convertedTransactions);
            if (convertedPurchase != null) {
                llmPayload.put("hypotheticalPurchase", convertedPurchase);
                if (convertedPurchase.containsKey("amount")) {
                    llmPayload.put("purchaseAmount", convertedPurchase.get("amount"));
                }
            }

            try {
                String payloadJson = objectMapper.writeValueAsString(llmPayload);
                String systemPrompt = buildSystemPrompt(preferredCurrency, currencySymbol, languageName);
                log.info("Decision Assistant LLM system prompt: {}", systemPrompt);
                log.info("Decision Assistant LLM payload: {}", payloadJson);
                llmRecommendation = llmService.generateCompletion(systemPrompt, payloadJson);
            } catch (Exception e) {
                llmRecommendation = "⚠️ [AI Assistant Offline Fallback] Could not serialize financial context: " + e.getMessage();
            }

            // 4. Safety net: if final LLM response contains "$" while user's currency is not USD, replace with correct symbol
            if (llmRecommendation != null && !"USD".equalsIgnoreCase(preferredCurrency) && llmRecommendation.contains("$")) {
                llmRecommendation = llmRecommendation.replace("$", currencySymbol);
            }

            // 5. If LLM timed out or failed, produce localized guidance assessment with Western digits and proper disclaimer
            if (llmRecommendation == null || llmRecommendation.startsWith("⚠️ [AI Assistant Offline Fallback]")) {
                String lang = languageCode != null ? languageCode.trim().toLowerCase() : "en";
                String localizedDisclaimer = switch (lang) {
                    case "ta" -> "இது தொழில்முறை நிதி ஆலோசனை அல்ல.";
                    case "hi" -> "यह पेशेवर वित्तीय सलाह नहीं है।";
                    case "ar" -> "هذه ليست نصيحة مالية مهنية.";
                    case "ja" -> "これは専門的な財務アドバイスではありません。";
                    default -> "This is not professional financial advice.";
                };
                if ("ta".equals(lang)) {
                    llmRecommendation = String.format(
                            "உங்கள் தற்போதைய நிதி நிலவரத்தின்படி, உங்கள் மாதாந்திர வருமானம் %s%s மற்றும் மாதாந்திர செலவுகள் %s%s ஆகும். கணக்கீட்டு இருப்பு %s%s. புதிய செலவுகளை செய்வதற்கு முன் பட்ஜெட்டை சரிபார்க்கவும்.\n\n%s",
                            currencySymbol, convertMonetary(snapshot.monthlyIncome(), preferredCurrency),
                            currencySymbol, convertMonetary(snapshot.monthlyExpenses(), preferredCurrency),
                            currencySymbol, convertMonetary(snapshot.totalBalance(), preferredCurrency),
                            localizedDisclaimer
                    );
                } else if ("hi".equals(lang)) {
                    llmRecommendation = String.format(
                            "आपकी वर्तमान वित्तीय स्थिति के अनुसार, आपकी मासिक आय %s%s और मासिक व्यय %s%s है। गणना की गई शेष राशि %s%s है।\n\n%s",
                            currencySymbol, convertMonetary(snapshot.monthlyIncome(), preferredCurrency),
                            currencySymbol, convertMonetary(snapshot.monthlyExpenses(), preferredCurrency),
                            currencySymbol, convertMonetary(snapshot.totalBalance(), preferredCurrency),
                            localizedDisclaimer
                    );
                }
            }
        }

        return new DecisionAssistantResponse(
                userQuestion,
                purchase,
                snapshot,
                null,
                activeGoals,
                recentTransactions,
                null,
                null,
                null,
                llmRecommendation,
                LocalDateTime.now()
        );
    }

    /**
     * Evaluates Buy Now, Wait Option, and EMI Option deterministically.
     * All arithmetic is computed purely before packaging for the LLM.
     */
    private DecisionAssistantResponse.ScenarioEvaluations evaluatePrecomputedScenarios(
            DecisionAssistantRequest.HypotheticalPurchase purchase,
            DecisionAssistantResponse.FinancialSnapshot snapshot,
            List<SavingsGoal> goals,
            FinancialRecommendationResponse recommendations,
            List<Transaction> expenses,
            List<Income> incomes
    ) {
        return evaluatePrecomputedScenarios(purchase, snapshot, goals, recommendations, expenses, incomes, "USD");
    }

    private DecisionAssistantResponse.ScenarioEvaluations evaluatePrecomputedScenarios(
            DecisionAssistantRequest.HypotheticalPurchase purchase,
            DecisionAssistantResponse.FinancialSnapshot snapshot,
            List<SavingsGoal> goals,
            FinancialRecommendationResponse recommendations,
            List<Transaction> expenses,
            List<Income> incomes,
            String preferredCurrency
    ) {
        BigDecimal currentSavings = snapshot.totalBalance();
        BigDecimal purchaseCost = purchase.getAmount();

        // Reserve floor: based on recommendations benchmark or 3x monthly outflow
        BigDecimal reserveFloor = recommendations.reserveThreshold();
        if (reserveFloor == null || reserveFloor.compareTo(BigDecimal.ZERO) <= 0) {
            BigDecimal monthlyOutflow = snapshot.monthlyExpenses().add(snapshot.recurringBillsMonthly());
            reserveFloor = monthlyOutflow.multiply(BigDecimal.valueOf(3)).setScale(2, RoundingMode.HALF_UP);
        }

        // Determine savings goal context
        DecisionScenarioEvaluator.SavingsGoalContext goalContext = DecisionScenarioEvaluator.SavingsGoalContext.noGoal();
        if (goals != null && !goals.isEmpty()) {
            SavingsGoal primaryGoal = goals.get(0);
            if (primaryGoal != null && primaryGoal.getTargetAmount() != null && primaryGoal.getTargetAmount().compareTo(BigDecimal.ZERO) > 0) {
                goalContext = DecisionScenarioEvaluator.SavingsGoalContext.of(
                        primaryGoal.getName(),
                        primaryGoal.getTargetAmount(),
                        primaryGoal.getCurrentAmount()
                );
            }
        }

        // Actual last 6 months net savings history (Income minus Expenses for each of the last 6 months)
        List<BigDecimal> monthlyNetSavingsHistory = computeLast6MonthsActualNetSavings(expenses, incomes);
        BigDecimal sumNetSavings = BigDecimal.ZERO;
        for (BigDecimal val : monthlyNetSavingsHistory) {
            if (val != null) {
                sumNetSavings = sumNetSavings.add(val);
            }
        }
        int count = monthlyNetSavingsHistory.isEmpty() ? 1 : monthlyNetSavingsHistory.size();
        BigDecimal avgMonthlyNetSavings = sumNetSavings.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

        // 1. Evaluate Buy Now
        DecisionScenarioResult buyNowResult = DecisionScenarioEvaluator.evaluateBuyNow(
                currentSavings, purchaseCost, reserveFloor, goalContext, avgMonthlyNetSavings, preferredCurrency
        );

        // 2. Evaluate Wait Option (Earliest Safe Month)
        DecisionScenarioResult waitOptionResult = DecisionScenarioEvaluator.evaluateWaitOption(
                currentSavings, purchaseCost, reserveFloor, monthlyNetSavingsHistory, goalContext, preferredCurrency
        );

        // 3. Evaluate Custom Timing Option (Manually Chosen Month)
        int customMonths = parseTimingToMonths(purchase.getTiming());
        DecisionScenarioResult customTimingResult = DecisionScenarioEvaluator.evaluateCustomTimingOption(
                currentSavings, purchaseCost, reserveFloor, monthlyNetSavingsHistory, customMonths, goalContext, preferredCurrency
        );

        // 4. Evaluate EMI Option
        int tenure = (purchase.getEmiTenureMonths() != null && purchase.getEmiTenureMonths() > 0) ? purchase.getEmiTenureMonths() : 6;
        BigDecimal emiAmount = purchase.getEmiMonthlyAmount();

        BigDecimal salary = snapshot.monthlyIncome();
        BigDecimal fixedExpenses = snapshot.recurringBillsMonthly();

        BigDecimal minSavingsGoal = BigDecimal.ZERO;
        if (goalContext.hasGoal() && !goalContext.isAlreadyMet()) {
            minSavingsGoal = goalContext.remainingAmount().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
        } else if (salary.compareTo(BigDecimal.ZERO) > 0) {
            minSavingsGoal = salary.multiply(BigDecimal.valueOf(0.20)).setScale(2, RoundingMode.HALF_UP);
        }

        DecisionScenarioResult emiOptionResult = DecisionScenarioEvaluator.evaluateEmiOption(
                purchaseCost, emiAmount, salary, fixedExpenses, minSavingsGoal, BigDecimal.valueOf(0.30), goalContext, tenure, preferredCurrency
        );

        return new DecisionAssistantResponse.ScenarioEvaluations(
                buyNowResult,
                waitOptionResult,
                emiOptionResult,
                customTimingResult
        );
    }

    /**
     * Extracts actual historical net savings (income minus expenses) for each of the last 6 months.
     */
    public List<BigDecimal> computeLast6MonthsActualNetSavings(List<Transaction> expenses, List<Income> incomes) {
        YearMonth currentYM = YearMonth.now();
        List<YearMonth> last6Months = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            last6Months.add(currentYM.minusMonths(i));
        }

        Map<YearMonth, BigDecimal> monthlyExpensesMap = new HashMap<>();
        Map<YearMonth, BigDecimal> monthlyIncomesMap = new HashMap<>();
        for (YearMonth ym : last6Months) {
            monthlyExpensesMap.put(ym, BigDecimal.ZERO);
            monthlyIncomesMap.put(ym, BigDecimal.ZERO);
        }

        for (Transaction t : expenses) {
            if (t.getCreatedAt() != null) {
                YearMonth ym = YearMonth.from(t.getCreatedAt());
                if (monthlyExpensesMap.containsKey(ym)) {
                    monthlyExpensesMap.put(ym, monthlyExpensesMap.get(ym).add(t.getAmount()));
                }
            }
        }

        for (Income inc : incomes) {
            if (inc.getCreatedAt() != null) {
                YearMonth ym = YearMonth.from(inc.getCreatedAt());
                if (monthlyIncomesMap.containsKey(ym)) {
                    monthlyIncomesMap.put(ym, monthlyIncomesMap.get(ym).add(inc.getAmount()));
                }
            }
        }

        List<BigDecimal> netSavingsHistory = new ArrayList<>();
        for (YearMonth ym : last6Months) {
            BigDecimal inc = monthlyIncomesMap.get(ym);
            BigDecimal exp = monthlyExpensesMap.get(ym);
            netSavingsHistory.add(inc.subtract(exp).setScale(2, RoundingMode.HALF_UP));
        }

        return netSavingsHistory;
    }

    private DecisionAssistantResponse.FinancialSnapshot buildFinancialSnapshot(
            List<Transaction> expenses,
            List<Income> incomes,
            List<Subscription> subs
    ) {
        LocalDateTime now = LocalDateTime.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();

        BigDecimal totalIncome = incomes.stream().map(Income::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpenses = expenses.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalBalance = totalIncome.subtract(totalExpenses);

        BigDecimal monthlyIncome = incomes.stream()
                .filter(i -> i.getCreatedAt() != null && i.getCreatedAt().getMonthValue() == currentMonth && i.getCreatedAt().getYear() == currentYear)
                .map(Income::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyExpenses = expenses.stream()
                .filter(e -> e.getCreatedAt() != null && e.getCreatedAt().getMonthValue() == currentMonth && e.getCreatedAt().getYear() == currentYear)
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlySavings = monthlyIncome.subtract(monthlyExpenses);
        double savingsRatio = 0.0;
        if (monthlyIncome.compareTo(BigDecimal.ZERO) > 0) {
            savingsRatio = monthlySavings.divide(monthlyIncome, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
        }

        BigDecimal recurringBills = subs.stream().map(Subscription::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> categorySums = expenses.stream()
                .collect(Collectors.groupingBy(e -> e.getCategory() != null ? e.getCategory() : "Uncategorized", Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)));

        List<DashboardStatsResponse.CategoryShare> breakdown = new ArrayList<>();
        if (totalExpenses.compareTo(BigDecimal.ZERO) > 0) {
            for (Map.Entry<String, BigDecimal> entry : categorySums.entrySet()) {
                double pct = entry.getValue().divide(totalExpenses, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
                breakdown.add(new DashboardStatsResponse.CategoryShare(entry.getKey(), entry.getValue(), Math.round(pct * 10.0) / 10.0));
            }
        }
        breakdown.sort((a, b) -> b.amount().compareTo(a.amount()));

        return new DecisionAssistantResponse.FinancialSnapshot(
                totalBalance.setScale(2, RoundingMode.HALF_UP),
                totalIncome.setScale(2, RoundingMode.HALF_UP),
                totalExpenses.setScale(2, RoundingMode.HALF_UP),
                monthlyIncome.setScale(2, RoundingMode.HALF_UP),
                monthlyExpenses.setScale(2, RoundingMode.HALF_UP),
                monthlySavings.setScale(2, RoundingMode.HALF_UP),
                Math.round(savingsRatio * 10.0) / 10.0,
                recurringBills.setScale(2, RoundingMode.HALF_UP),
                breakdown
        );
    }

    private WhatIfResponse runWhatIfSimulation(
            String username,
            DecisionAssistantRequest.HypotheticalPurchase purchase,
            List<Transaction> realExpenses,
            List<Income> realIncomes,
            List<Subscription> realSubs,
            ThreeMonthCashFlowResponse baseCF,
            RiskScoreResponse baseRisk
    ) {
        WhatIfResponse.ProjectionState baselineState = buildProjectionState(baseCF, baseRisk);

        LocalDateTime simDate = parseTimingToDateTime(purchase.getTiming());

        List<Transaction> simExpenses = new ArrayList<>(realExpenses);
        Transaction simTx = new Transaction();
        simTx.setAmount(purchase.getAmount());
        simTx.setDescription("[SIMULATION] " + (purchase.getDescription() != null ? purchase.getDescription() : "Hypothetical Purchase"));
        simTx.setCategory("Shopping & Tech");
        simTx.setPaymentMethod("Credit");
        simTx.setUsername(username);
        simTx.setCreatedAt(simDate);
        simExpenses.add(simTx);

        ThreeMonthCashFlowResponse simCF = compute3MonthCashFlow(simExpenses, realIncomes, realSubs);
        RiskScoreResponse simRisk = computeRiskScore(simExpenses, realIncomes, realSubs);
        WhatIfResponse.ProjectionState simulatedState = buildProjectionState(simCF, simRisk);

        String desc = purchase.getDescription() != null ? purchase.getDescription() : "Hypothetical Purchase";
        WhatIfResponse.DiffSummary diffSummary = buildDiffSummary(baselineState, simulatedState, purchase.getAmount(), desc);

        return new WhatIfResponse(
                desc,
                purchase.getAmount(),
                simDate.toLocalDate().toString(),
                baselineState,
                simulatedState,
                diffSummary
        );
    }

    public int parseTimingToMonths(String timing) {
        if (timing == null || timing.trim().isEmpty()) {
            return 0;
        }
        String t = timing.trim().toLowerCase();
        if (t.equals("now") || t.equals("immediate") || t.equals("today")) {
            return 0;
        }

        // 1. Try parsing YearMonth format "YYYY-MM"
        try {
            YearMonth targetYM = YearMonth.parse(timing.trim());
            YearMonth currentYM = YearMonth.now();
            return (int) java.time.temporal.ChronoUnit.MONTHS.between(currentYM, targetYM);
        } catch (Exception ignored) {}

        // 2. Try parsing LocalDate format "YYYY-MM-DD"
        try {
            LocalDate targetDate = LocalDate.parse(timing.trim());
            YearMonth targetYM = YearMonth.from(targetDate);
            YearMonth currentYM = YearMonth.now();
            return (int) java.time.temporal.ChronoUnit.MONTHS.between(currentYM, targetYM);
        } catch (Exception ignored) {}

        // 3. Match regex "in N months" or "N"
        Pattern pattern = Pattern.compile("(\\d+)");
        Matcher matcher = pattern.matcher(t);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        return 0;
    }

    private LocalDateTime parseTimingToDateTime(String timing) {
        if (timing == null || timing.trim().isEmpty()) {
            return LocalDateTime.now();
        }

        String t = timing.trim().toLowerCase();
        if (t.equals("now") || t.equals("immediate") || t.equals("today")) {
            return LocalDateTime.now();
        }

        // Try YearMonth
        try {
            YearMonth ym = YearMonth.parse(timing.trim());
            return ym.atDay(1).atStartOfDay();
        } catch (Exception ignored) {}

        // Try LocalDate
        try {
            return LocalDate.parse(timing.trim()).atStartOfDay();
        } catch (Exception ignored) {}

        Pattern pattern = Pattern.compile("(\\d+)\\s*month");
        Matcher matcher = pattern.matcher(t);
        if (matcher.find()) {
            int months = Integer.parseInt(matcher.group(1));
            return LocalDateTime.now().plusMonths(months);
        }

        return LocalDateTime.now();
    }

    public GoalResponse mapGoalToResponse(SavingsGoal goal, List<Income> incomes, List<Transaction> expenses) {
        return mapGoalToResponse(goal, incomes, expenses, "USD");
    }

    public GoalResponse mapGoalToResponse(SavingsGoal goal, List<Income> incomes, List<Transaction> expenses, String preferredCurrency) {
        BigDecimal target = goal.getTargetAmount();
        BigDecimal current = goal.getCurrentAmount();
        BigDecimal remaining = target.subtract(current);
        if (remaining.compareTo(BigDecimal.ZERO) < 0) remaining = BigDecimal.ZERO;

        double progressPct = 0.0;
        if (target.compareTo(BigDecimal.ZERO) > 0) {
            progressPct = current.divide(target, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
            if (progressPct > 100.0) progressPct = 100.0;
        }

        LocalDateTime now = LocalDateTime.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();

        BigDecimal monthlyIncome = incomes.stream()
                .filter(i -> i.getCreatedAt() != null && i.getCreatedAt().getMonthValue() == currentMonth && i.getCreatedAt().getYear() == currentYear)
                .map(Income::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyExpenses = expenses.stream()
                .filter(e -> e.getCreatedAt() != null && e.getCreatedAt().getMonthValue() == currentMonth && e.getCreatedAt().getYear() == currentYear)
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlySavingsRate = monthlyIncome.subtract(monthlyExpenses);

        String currency = (preferredCurrency != null && !preferredCurrency.isBlank()) ? preferredCurrency : "USD";
        String zeroFormatted = (currencyConversionService != null)
                ? currencyConversionService.formatAmount(BigDecimal.ZERO, currency)
                : CurrencyConversionService.formatMoney(BigDecimal.ZERO, currency);

        double estMonths = -1;
        String estCompletionDate = "Indefinite (Monthly Savings <= " + zeroFormatted + ")";

        if (current.compareTo(target) >= 0) {
            estMonths = 0;
            estCompletionDate = "Goal Completed! 🎉";
        } else if (monthlySavingsRate.compareTo(BigDecimal.ZERO) > 0) {
            estMonths = remaining.divide(monthlySavingsRate, 2, RoundingMode.HALF_UP).doubleValue();
            long monthsToAdd = (long) Math.ceil(estMonths);
            YearMonth completionYM = YearMonth.now().plusMonths(monthsToAdd);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
            estCompletionDate = String.format("Est. %s (~%.1f mos)", completionYM.format(formatter), estMonths);
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
                goal.getCreatedAt()
        );
    }

    public WhatIfResponse.ProjectionState buildProjectionState(ThreeMonthCashFlowResponse cf, RiskScoreResponse risk) {
        BigDecimal end3M = cf.projections().isEmpty() ? cf.startingBalance() : cf.projections().get(cf.projections().size() - 1).endBalance();
        return new WhatIfResponse.ProjectionState(
                cf.startingBalance(), end3M, risk.totalRiskScore(), risk.riskLevel(), cf.projections()
        );
    }

    public WhatIfResponse.DiffSummary buildDiffSummary(WhatIfResponse.ProjectionState base, WhatIfResponse.ProjectionState sim, BigDecimal amount, String label) {
        return buildDiffSummary(base, sim, amount, label, "USD");
    }

    public WhatIfResponse.DiffSummary buildDiffSummary(WhatIfResponse.ProjectionState base, WhatIfResponse.ProjectionState sim, BigDecimal amount, String label, String preferredCurrency) {
        BigDecimal balImpact = sim.startingBalance().subtract(base.startingBalance());
        BigDecimal month3Impact = sim.month3EndingBalance().subtract(base.month3EndingBalance());
        int riskScoreImpact = sim.totalRiskScore() - base.totalRiskScore();
        boolean riskChanged = !base.riskLevel().equals(sim.riskLevel());
        String riskShift = base.riskLevel() + " → " + sim.riskLevel();

        String currency = (preferredCurrency != null && !preferredCurrency.isBlank()) ? preferredCurrency : "USD";
        String formattedAmt = (currencyConversionService != null) ? currencyConversionService.formatAmount(amount, currency) : CurrencyConversionService.formatMoney(amount, currency);
        String formattedM3 = (currencyConversionService != null) ? currencyConversionService.formatAmount(month3Impact, currency) : CurrencyConversionService.formatMoney(month3Impact, currency);
        String formattedBase = (currencyConversionService != null) ? currencyConversionService.formatAmount(base.month3EndingBalance(), currency) : CurrencyConversionService.formatMoney(base.month3EndingBalance(), currency);
        String formattedSim = (currencyConversionService != null) ? currencyConversionService.formatAmount(sim.month3EndingBalance(), currency) : CurrencyConversionService.formatMoney(sim.month3EndingBalance(), currency);

        String summary = String.format(
                "%s of %s impacts 3-month ending balance by %s (from %s to %s) and changes risk score by %+d pts (%s). ZERO DB rows modified.",
                label, formattedAmt, formattedM3, formattedBase, formattedSim, riskScoreImpact, riskShift
        );

        return new WhatIfResponse.DiffSummary(balImpact, month3Impact, riskScoreImpact, riskChanged, riskShift, summary);
    }

    public ThreeMonthCashFlowResponse compute3MonthCashFlow(
            List<Transaction> expenses,
            List<Income> incomes,
            List<Subscription> subs
    ) {
        BigDecimal totalIncome = incomes.stream().map(Income::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpenses = expenses.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal startBalance = totalIncome.subtract(totalExpenses);

        LocalDateTime now = LocalDateTime.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();

        BigDecimal expectedIncome = incomes.stream()
                .filter(i -> i.getCreatedAt() != null && i.getCreatedAt().getMonthValue() == currentMonth && i.getCreatedAt().getYear() == currentYear)
                .map(Income::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal recurringBills = subs.stream().map(Subscription::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        YearMonth currentYM = YearMonth.now();
        List<YearMonth> last6Months = new ArrayList<>();
        for (int i = 5; i >= 0; i--) last6Months.add(currentYM.minusMonths(i));

        Map<String, double[]> categoryMonthlyValues = new HashMap<>();
        for (Transaction t : expenses) {
            if (t.getCreatedAt() != null) {
                YearMonth ym = YearMonth.from(t.getCreatedAt());
                int monthIndex = last6Months.indexOf(ym);
                if (monthIndex >= 0) {
                    String cat = t.getCategory() != null ? t.getCategory() : "Uncategorized";
                    double[] vals = categoryMonthlyValues.computeIfAbsent(cat, k -> new double[6]);
                    vals[monthIndex] += t.getAmount().doubleValue();
                }
            }
        }

        Map<String, double[]> categoryModels = new HashMap<>();
        for (Map.Entry<String, double[]> entry : categoryMonthlyValues.entrySet()) {
            double[] y = entry.getValue();
            double sumY = 0.0;
            for (double v : y) sumY += v;
            double yBar = sumY / 6.0;

            double numerator = 0.0;
            for (int i = 0; i < 6; i++) {
                numerator += (i + 1 - 3.5) * (y[i] - yBar);
            }
            double slope = numerator / 17.5;
            double intercept = yBar - (slope * 3.5);
            categoryModels.put(entry.getKey(), new double[]{slope, intercept});
        }

        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
        List<ThreeMonthCashFlowResponse.MonthlyCashFlowProjection> projections = new ArrayList<>();

        BigDecimal currentRunningBalance = startBalance;

        for (int k = 1; k <= 3; k++) {
            YearMonth targetYM = currentYM.plusMonths(k);
            String monthName = targetYM.format(monthFormatter);
            double xTarget = 6.0 + k;

            double monthVarExpenses = 0.0;
            for (double[] model : categoryModels.values()) {
                double proj = (model[0] * xTarget) + model[1];
                if (proj > 0) monthVarExpenses += proj;
            }

            BigDecimal varExpensesBD = BigDecimal.valueOf(monthVarExpenses).setScale(2, RoundingMode.HALF_UP);
            BigDecimal totalOutflow = varExpensesBD.add(recurringBills);
            BigDecimal netFlow = expectedIncome.subtract(totalOutflow);
            BigDecimal endBalance = currentRunningBalance.add(netFlow);

            projections.add(new ThreeMonthCashFlowResponse.MonthlyCashFlowProjection(
                    monthName,
                    currentRunningBalance.setScale(2, RoundingMode.HALF_UP),
                    expectedIncome.setScale(2, RoundingMode.HALF_UP),
                    varExpensesBD,
                    recurringBills.setScale(2, RoundingMode.HALF_UP),
                    netFlow.setScale(2, RoundingMode.HALF_UP),
                    endBalance.setScale(2, RoundingMode.HALF_UP)
            ));

            currentRunningBalance = endBalance;
        }

        return new ThreeMonthCashFlowResponse(
                startBalance.setScale(2, RoundingMode.HALF_UP),
                expectedIncome.setScale(2, RoundingMode.HALF_UP),
                recurringBills.setScale(2, RoundingMode.HALF_UP),
                projections
        );
    }

    public RiskScoreResponse computeRiskScore(
            List<Transaction> expenses,
            List<Income> incomes,
            List<Subscription> subs
    ) {
        LocalDateTime now = LocalDateTime.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();

        BigDecimal monthlyIncomeBD = incomes.stream()
                .filter(i -> i.getCreatedAt() != null && i.getCreatedAt().getMonthValue() == currentMonth && i.getCreatedAt().getYear() == currentYear)
                .map(Income::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal monthlyExpensesBD = expenses.stream()
                .filter(e -> e.getCreatedAt() != null && e.getCreatedAt().getMonthValue() == currentMonth && e.getCreatedAt().getYear() == currentYear)
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal recurringBillsBD = subs.stream().map(Subscription::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<RiskScoreResponse.RiskFactor> factors = new ArrayList<>();
        int totalRiskScore = 0;

        double savingsRatio = 0.0;
        if (monthlyIncomeBD.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal savings = monthlyIncomeBD.subtract(monthlyExpensesBD);
            savingsRatio = savings.divide(monthlyIncomeBD, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
        }

        int s1Score = 0;
        String s1Status = "HEALTHY";
        String s1Desc = String.format("Savings ratio is %.1f%%.", savingsRatio);
        LocalizedMessage s1FactorMsg = new LocalizedMessage("risk.factor.savingsRatio");
        LocalizedMessage s1DescMsg;

        if (monthlyIncomeBD.compareTo(BigDecimal.ZERO) == 0) {
            s1Score = 20; s1Status = "HIGH_RISK"; s1Desc = "No monthly income recorded.";
            s1DescMsg = new LocalizedMessage("risk.savings.none");
        } else if (savingsRatio >= 30.0) {
            s1Score = 0; s1Status = "HEALTHY";
            s1DescMsg = new LocalizedMessage("risk.savings.ratio", Map.of("ratio", String.format(Locale.ROOT, "%.1f", savingsRatio)));
        } else if (savingsRatio >= 15.0) {
            s1Score = 10; s1Status = "MODERATE_RISK"; s1Desc = String.format("Savings ratio is %.1f%% (moderate risk).", savingsRatio);
            s1DescMsg = new LocalizedMessage("risk.savings.moderate", Map.of("ratio", String.format(Locale.ROOT, "%.1f", savingsRatio)));
        } else if (savingsRatio >= 0.0) {
            s1Score = 20; s1Status = "HIGH_RISK"; s1Desc = String.format("Savings ratio is %.1f%% (below 15%% threshold).", savingsRatio);
            s1DescMsg = new LocalizedMessage("risk.savings.below15", Map.of("ratio", String.format(Locale.ROOT, "%.1f", savingsRatio)));
        } else {
            s1Score = 30; s1Status = "HIGH_RISK"; s1Desc = String.format("Negative savings ratio (%.1f%%).", savingsRatio);
            s1DescMsg = new LocalizedMessage("risk.savings.negative", Map.of("ratio", String.format(Locale.ROOT, "%.1f", savingsRatio)));
        }
        factors.add(new RiskScoreResponse.RiskFactor("Savings Ratio Risk", s1Score, 30, s1Status, s1Desc, s1FactorMsg, s1DescMsg));
        totalRiskScore += s1Score;

        double subRatio = 0.0;
        if (monthlyIncomeBD.compareTo(BigDecimal.ZERO) > 0) {
            subRatio = recurringBillsBD.divide(monthlyIncomeBD, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
        }

        int s2Score = 0;
        String s2Status = "HEALTHY";
        String s2Desc = String.format("Recurring bills account for %.1f%% of income.", subRatio);
        LocalizedMessage s2FactorMsg = new LocalizedMessage("risk.factor.fixedSubs");
        LocalizedMessage s2DescMsg;

        if (subRatio <= 15.0) {
            s2Score = 0; s2Status = "HEALTHY";
            s2DescMsg = new LocalizedMessage("risk.subs.healthy", Map.of("ratio", String.format(Locale.ROOT, "%.1f", subRatio)));
        } else if (subRatio <= 30.0) {
            s2Score = 12; s2Status = "MODERATE_RISK"; s2Desc = String.format("Fixed subscriptions account for %.1f%% of income.", subRatio);
            s2DescMsg = new LocalizedMessage("risk.subs.moderate", Map.of("ratio", String.format(Locale.ROOT, "%.1f", subRatio)));
        } else {
            s2Score = 25; s2Status = "HIGH_RISK"; s2Desc = String.format("High fixed obligations at %.1f%% of income.", subRatio);
            s2DescMsg = new LocalizedMessage("risk.subs.high", Map.of("ratio", String.format(Locale.ROOT, "%.1f", subRatio)));
        }
        factors.add(new RiskScoreResponse.RiskFactor("Fixed Subscriptions Ratio", s2Score, 25, s2Status, s2Desc, s2FactorMsg, s2DescMsg));
        totalRiskScore += s2Score;

        YearMonth currentYM = YearMonth.now();
        YearMonth prevYM = currentYM.minusMonths(1);
        BigDecimal currentMonthAmt = expenses.stream()
                .filter(e -> e.getCreatedAt() != null && YearMonth.from(e.getCreatedAt()).equals(currentYM))
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal prevMonthAmt = expenses.stream()
                .filter(e -> e.getCreatedAt() != null && YearMonth.from(e.getCreatedAt()).equals(prevYM))
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        double momGrowth = 0.0;
        if (prevMonthAmt.compareTo(BigDecimal.ZERO) > 0) {
            momGrowth = currentMonthAmt.subtract(prevMonthAmt).divide(prevMonthAmt, 4, RoundingMode.HALF_UP).doubleValue() * 100.0;
        } else if (currentMonthAmt.compareTo(BigDecimal.ZERO) > 0) {
            momGrowth = 100.0;
        }

        int s3Score = 0;
        String s3Status = "HEALTHY";
        String s3Desc = String.format("Expense growth is %.1f%% MoM.", momGrowth);
        LocalizedMessage s3FactorMsg = new LocalizedMessage("risk.factor.expenseGrowth");
        LocalizedMessage s3DescMsg;

        if (momGrowth <= 0.0) {
            s3Score = 0; s3Status = "HEALTHY";
            s3DescMsg = new LocalizedMessage("risk.growth.healthy", Map.of("growth", String.format(Locale.ROOT, "%.1f", momGrowth)));
        } else if (momGrowth <= 15.0) {
            s3Score = 10; s3Status = "MODERATE_RISK"; s3Desc = String.format("Expense growth is +%.1f%% MoM.", momGrowth);
            s3DescMsg = new LocalizedMessage("risk.growth.moderate", Map.of("growth", String.format(Locale.ROOT, "%.1f", momGrowth)));
        } else {
            s3Score = 25; s3Status = "HIGH_RISK"; s3Desc = String.format("Surging expense growth of +%.1f%% MoM.", momGrowth);
            s3DescMsg = new LocalizedMessage("risk.growth.high", Map.of("growth", String.format(Locale.ROOT, "%.1f", momGrowth)));
        }
        factors.add(new RiskScoreResponse.RiskFactor("MoM Expense Growth Trend", s3Score, 25, s3Status, s3Desc, s3FactorMsg, s3DescMsg));
        totalRiskScore += s3Score;

        int s4Score = 0;
        String s4Status = "HEALTHY";
        String s4Desc = "Projections remain positive across all 3 upcoming months.";
        LocalizedMessage s4FactorMsg = new LocalizedMessage("risk.factor.runwayDeficit");
        LocalizedMessage s4DescMsg = new LocalizedMessage("risk.runway.healthy");

        if (monthlyExpensesBD.add(recurringBillsBD).compareTo(monthlyIncomeBD) > 0) {
            s4Score = 10; s4Status = "MODERATE_RISK"; s4Desc = "Current monthly outflow exceeds income.";
            s4DescMsg = new LocalizedMessage("risk.runway.deficit");
        }
        factors.add(new RiskScoreResponse.RiskFactor("3-Month Runway Deficit Risk", s4Score, 20, s4Status, s4Desc, s4FactorMsg, s4DescMsg));
        totalRiskScore += s4Score;

        String riskLevel = "LOW";
        if (totalRiskScore >= 56) riskLevel = "HIGH";
        else if (totalRiskScore >= 26) riskLevel = "MEDIUM";

        return new RiskScoreResponse(totalRiskScore, riskLevel, factors);
    }
}
