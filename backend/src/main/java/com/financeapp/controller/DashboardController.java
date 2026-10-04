package com.financeapp.controller;

import com.financeapp.dto.*;
import com.financeapp.model.Income;
import com.financeapp.model.SavingsGoal;
import com.financeapp.model.Subscription;
import com.financeapp.model.Transaction;
import com.financeapp.model.User;
import com.financeapp.repository.IncomeRepository;
import com.financeapp.repository.SavingsGoalRepository;
import com.financeapp.repository.SubscriptionRepository;
import com.financeapp.repository.TransactionRepository;
import com.financeapp.repository.UserRepository;
import com.financeapp.service.CategoryService;
import com.financeapp.service.CurrencyConversionService;
import com.financeapp.service.RecommendationService;
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
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class DashboardController {

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

    @Autowired(required = false)
    private UserRepository userRepository;

    @Autowired(required = false)
    private CurrencyConversionService currencyConversionService;

    @Autowired(required = false)
    private com.financeapp.service.WhatIfSimulationService whatIfSimulationService;

    private String getPreferredCurrency(String username) {
        if (username != null && userRepository != null) {
            return userRepository.findByUsername(username)
                    .map(User::getPreferredCurrency)
                    .filter(c -> !c.isBlank())
                    .orElse("USD");
        }
        return "USD";
    }

    private String formatAmount(BigDecimal usdAmount, String currencyCode) {
        if (currencyConversionService != null) {
            return currencyConversionService.formatAmount(usdAmount, currencyCode);
        }
        return CurrencyConversionService.formatMoney(usdAmount, currencyCode);
    }

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, String>> getDashboard(Authentication authentication) {
        return ResponseEntity.ok(Map.of(
                "message", "Welcome to your Finance Dashboard!",
                "user", authentication.getName()
        ));
    }

    @GetMapping("/dashboard/recommendations")
    public ResponseEntity<FinancialRecommendationResponse> getRecommendations(Authentication authentication) {
        String username = authentication != null ? authentication.getName() : null;
        String preferredCurrency = getPreferredCurrency(username);

        List<Transaction> expenses = transactionRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Income> incomes = incomeRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Subscription> subs = subscriptionRepository != null ? subscriptionRepository.findByUsernameAndIsActiveTrue(username) : Collections.emptyList();
        List<SavingsGoal> goals = savingsGoalRepository != null ? savingsGoalRepository.findByUsernameOrderByCreatedAtDesc(username) : Collections.emptyList();

        ThreeMonthCashFlowResponse cashFlow = compute3MonthCashFlowInternal(expenses, incomes, subs);
        RiskScoreResponse riskScore = computeRiskScoreInternal(expenses, incomes, subs);

        List<GoalResponse> goalResponses = new ArrayList<>();
        for (SavingsGoal g : goals) {
            goalResponses.add(mapGoalToResponse(g, username, incomes, expenses));
        }

        boolean hasHealthInsurance = false;
        boolean hasLifeInsurance = false;
        if (username != null && userRepository != null) {
            var userOpt = userRepository.findByUsername(username);
            if (userOpt.isPresent()) {
                hasHealthInsurance = Boolean.TRUE.equals(userOpt.get().getHasHealthInsurance());
                hasLifeInsurance = Boolean.TRUE.equals(userOpt.get().getHasLifeInsurance());
            }
        }

        return ResponseEntity.ok(recommendationService.evaluateRecommendations(
                cashFlow, riskScore, goalResponses, preferredCurrency, expenses, subs, hasHealthInsurance, hasLifeInsurance
        ));
    }

    @GetMapping("/dashboard/stats")
    public ResponseEntity<DashboardStatsResponse> getStats(Authentication authentication) {
        String username = authentication.getName();

        List<Transaction> expenses = transactionRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Income> incomes = incomeRepository.findByUsernameOrderByCreatedAtDesc(username);

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

        BigDecimal savings = monthlyIncome.subtract(monthlyExpenses);
        double savingsRatio = 0.0;
        if (monthlyIncome.compareTo(BigDecimal.ZERO) > 0) {
            savingsRatio = savings.divide(monthlyIncome, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
        }

        Map<String, BigDecimal> categorySums = expenses.stream()
                .collect(Collectors.groupingBy(e -> {
                    String raw = e.getCategory();
                    return (raw != null && CategoryService.isStandardCategory(raw) && !"Uncategorized".equalsIgnoreCase(raw))
                            ? CategoryService.normalizeCategory(raw)
                            : "Uncategorized";
                }, Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)));

        List<DashboardStatsResponse.CategoryShare> breakdown = new ArrayList<>();
        if (totalExpenses.compareTo(BigDecimal.ZERO) > 0) {
            for (Map.Entry<String, BigDecimal> entry : categorySums.entrySet()) {
                double pct = entry.getValue().divide(totalExpenses, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
                breakdown.add(new DashboardStatsResponse.CategoryShare(entry.getKey(), entry.getValue(), Math.round(pct * 10.0) / 10.0));
            }
        }
        breakdown.sort((a, b) -> b.amount().compareTo(a.amount()));

        return ResponseEntity.ok(new DashboardStatsResponse(
                totalBalance, monthlyIncome, monthlyExpenses, savings, Math.round(savingsRatio * 10.0) / 10.0, breakdown
        ));
    }

    @GetMapping("/dashboard/history")
    public ResponseEntity<HistoricalSpendingResponse> getHistoricalSpending(Authentication authentication) {
        String username = authentication.getName();
        List<Transaction> expenses = transactionRepository.findByUsernameOrderByCreatedAtDesc(username);

        YearMonth currentYM = YearMonth.now();
        List<YearMonth> last6Months = new ArrayList<>();
        for (int i = 5; i >= 0; i--) last6Months.add(currentYM.minusMonths(i));

        Map<YearMonth, BigDecimal> monthlyTotals = new HashMap<>();
        for (YearMonth ym : last6Months) monthlyTotals.put(ym, BigDecimal.ZERO);

        for (Transaction t : expenses) {
            if (t.getCreatedAt() != null) {
                YearMonth ym = YearMonth.from(t.getCreatedAt());
                if (monthlyTotals.containsKey(ym)) {
                    monthlyTotals.put(ym, monthlyTotals.get(ym).add(t.getAmount()));
                }
            }
        }

        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
        List<HistoricalSpendingResponse.MonthlyExpenseTotal> historyList = new ArrayList<>();

        for (YearMonth ym : last6Months) {
            historyList.add(new HistoricalSpendingResponse.MonthlyExpenseTotal(ym.format(monthFormatter), ym.toString(), monthlyTotals.get(ym)));
        }

        BigDecimal currentMonthAmt = monthlyTotals.get(currentYM);
        BigDecimal prevMonthAmt = monthlyTotals.getOrDefault(currentYM.minusMonths(1), BigDecimal.ZERO);

        double momPct = 0.0;
        String direction = "STABLE";

        if (prevMonthAmt.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = currentMonthAmt.subtract(prevMonthAmt);
            momPct = diff.divide(prevMonthAmt, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
            if (momPct > 0) direction = "UP";
            else if (momPct < 0) direction = "DOWN";
        } else if (currentMonthAmt.compareTo(BigDecimal.ZERO) > 0) {
            momPct = 100.0;
            direction = "UP";
        }

        return ResponseEntity.ok(new HistoricalSpendingResponse(Math.round(momPct * 10.0) / 10.0, direction, historyList));
    }

    @GetMapping("/dashboard/forecast")
    public ResponseEntity<ForecastResponse> getExpenseForecast(Authentication authentication) {
        String username = authentication.getName();
        List<Transaction> expenses = transactionRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Income> incomes = incomeRepository.findByUsernameOrderByCreatedAtDesc(username);

        YearMonth currentYM = YearMonth.now();
        YearMonth nextYM = currentYM.plusMonths(1);
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
        String nextMonthName = nextYM.format(monthFormatter);

        int currentMonth = currentYM.getMonthValue();
        int currentYear = currentYM.getYear();
        BigDecimal monthlyIncome = incomes.stream()
                .filter(i -> i.getCreatedAt() != null && i.getCreatedAt().getMonthValue() == currentMonth && i.getCreatedAt().getYear() == currentYear)
                .map(Income::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal recurringCommitmentsTotal = BigDecimal.ZERO;
        if (subscriptionRepository != null) {
            List<Subscription> activeSubs = subscriptionRepository.findByUsernameAndIsActiveTrue(username);
            recurringCommitmentsTotal = activeSubs.stream().map(Subscription::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        List<YearMonth> last6Months = new ArrayList<>();
        for (int i = 5; i >= 0; i--) last6Months.add(currentYM.minusMonths(i));

        Map<String, double[]> categoryMonthlyValues = new HashMap<>();
        for (Transaction t : expenses) {
            if (t.getCreatedAt() != null) {
                YearMonth ym = YearMonth.from(t.getCreatedAt());
                int monthIndex = last6Months.indexOf(ym);
                if (monthIndex >= 0) {
                    String rawCat = t.getCategory();
                    String cat = (rawCat != null && CategoryService.isStandardCategory(rawCat) && !"Uncategorized".equalsIgnoreCase(rawCat))
                            ? CategoryService.normalizeCategory(rawCat)
                            : "Uncategorized";
                    double[] vals = categoryMonthlyValues.computeIfAbsent(cat, k -> new double[6]);
                    vals[monthIndex] += t.getAmount().doubleValue();
                }
            }
        }

        List<ForecastResponse.CategoryForecast> categoryForecasts = new ArrayList<>();
        BigDecimal variableProjected = BigDecimal.ZERO;

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

            double projectedVal = (slope * 7.0) + intercept;
            if (projectedVal < 0) projectedVal = 0.0;

            BigDecimal projBD = BigDecimal.valueOf(projectedVal).setScale(2, RoundingMode.HALF_UP);
            variableProjected = variableProjected.add(projBD);

            String trendText = "Stable";
            String trendCode = "forecast.trend.stable";
            if (slope > 2.0) {
                trendText = "Trending Up";
                trendCode = "forecast.trend.up";
            } else if (slope < -2.0) {
                trendText = "Trending Down";
                trendCode = "forecast.trend.down";
            }

            categoryForecasts.add(new ForecastResponse.CategoryForecast(
                    entry.getKey(),
                    Math.round(slope * 100.0) / 100.0,
                    projBD,
                    trendText,
                    new LocalizedMessage(trendCode)
            ));
        }

        categoryForecasts.sort((a, b) -> b.projectedAmount().compareTo(a.projectedAmount()));

        BigDecimal totalProjected = variableProjected.add(recurringCommitmentsTotal);
        BigDecimal projectedNetCashFlow = monthlyIncome.subtract(totalProjected);

        String disclaimer = "This is a simple trend estimate based on linear regression over the last 6 months of transaction history plus fixed recurring commitments, not a probabilistic or guaranteed financial forecast.";
        LocalizedMessage disclaimerMsg = new LocalizedMessage("forecast.disclaimer");

        return ResponseEntity.ok(new ForecastResponse(
                nextMonthName,
                variableProjected,
                recurringCommitmentsTotal,
                totalProjected,
                projectedNetCashFlow,
                disclaimer,
                categoryForecasts,
                disclaimerMsg
        ));
    }

    @GetMapping("/dashboard/cashflow-3month")
    public ResponseEntity<ThreeMonthCashFlowResponse> get3MonthCashFlow(Authentication authentication) {
        String username = authentication.getName();
        List<Transaction> expenses = transactionRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Income> incomes = incomeRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Subscription> subs = subscriptionRepository != null ? subscriptionRepository.findByUsernameAndIsActiveTrue(username) : Collections.emptyList();

        return ResponseEntity.ok(compute3MonthCashFlowInternal(expenses, incomes, subs));
    }

    @GetMapping("/dashboard/risk-score")
    public ResponseEntity<RiskScoreResponse> getRiskScore(Authentication authentication) {
        String username = authentication.getName();
        List<Transaction> expenses = transactionRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Income> incomes = incomeRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Subscription> subs = subscriptionRepository != null ? subscriptionRepository.findByUsernameAndIsActiveTrue(username) : Collections.emptyList();

        return ResponseEntity.ok(computeRiskScoreInternal(expenses, incomes, subs));
    }

    @PostMapping("/dashboard/what-if")
    public ResponseEntity<WhatIfResponse> runWhatIfSimulation(
            @Valid @RequestBody WhatIfRequest request,
            Authentication authentication
    ) {
        String username = authentication.getName();
        List<Transaction> realExpenses = transactionRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Income> realIncomes = incomeRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Subscription> realSubs = subscriptionRepository != null ? subscriptionRepository.findByUsernameAndIsActiveTrue(username) : Collections.emptyList();

        ThreeMonthCashFlowResponse baseCF = compute3MonthCashFlowInternal(realExpenses, realIncomes, realSubs);
        RiskScoreResponse baseRisk = computeRiskScoreInternal(realExpenses, realIncomes, realSubs);

        WhatIfResponse.ProjectionState baselineState = buildProjectionState(baseCF, baseRisk);

        List<Transaction> simExpenses = new ArrayList<>(realExpenses);
        Transaction simTx = new Transaction();
        simTx.setAmount(request.getAmount());
        simTx.setDescription("[SIMULATION] " + request.getDescription());
        simTx.setCategory("Shopping & Tech");
        simTx.setPaymentMethod("Credit");
        simTx.setUsername(username);
        simTx.setCreatedAt(LocalDateTime.now());
        simExpenses.add(simTx);

        ThreeMonthCashFlowResponse simCF = compute3MonthCashFlowInternal(simExpenses, realIncomes, realSubs);
        RiskScoreResponse simRisk = computeRiskScoreInternal(simExpenses, realIncomes, realSubs);
        WhatIfResponse.ProjectionState simulatedState = buildProjectionState(simCF, simRisk);

        String preferredCurrency = getPreferredCurrency(username);
        WhatIfResponse.DiffSummary diffSummary = buildDiffSummary(baselineState, simulatedState, request.getAmount(), request.getDescription(), preferredCurrency);

        return ResponseEntity.ok(new WhatIfResponse(
                request.getDescription(), request.getAmount(),
                request.getDate() != null ? request.getDate() : LocalDateTime.now().toLocalDate().toString(),
                baselineState, simulatedState, diffSummary
        ));
    }

    @PostMapping("/dashboard/what-if/multi-scenario")
    public ResponseEntity<MultiScenarioWhatIfResponse> runMultiScenarioSimulation(
            @Valid @RequestBody MultiScenarioWhatIfRequest request,
            Authentication authentication
    ) {
        String username = authentication != null ? authentication.getName() : null;
        String preferredCurrency = getPreferredCurrency(username);

        List<Transaction> realExpenses = transactionRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Income> realIncomes = incomeRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Subscription> realSubs = subscriptionRepository != null ? subscriptionRepository.findByUsernameAndIsActiveTrue(username) : Collections.emptyList();

        ThreeMonthCashFlowResponse baseCF = compute3MonthCashFlowInternal(realExpenses, realIncomes, realSubs);
        RiskScoreResponse baseRisk = computeRiskScoreInternal(realExpenses, realIncomes, realSubs);
        WhatIfResponse.ProjectionState baselineState = buildProjectionState(baseCF, baseRisk);

        BigDecimal amount = request.getAmount();
        int delayMonths = request.getDelayMonths() > 0 ? request.getDelayMonths() : 3;
        int tenure = request.getEmiTenureMonths() > 0 ? request.getEmiTenureMonths() : 6;
        BigDecimal emiMonthly = request.getEmiMonthlyAmount() != null && request.getEmiMonthlyAmount().compareTo(BigDecimal.ZERO) > 0
                ? request.getEmiMonthlyAmount()
                : amount.divide(BigDecimal.valueOf(tenure), 2, RoundingMode.HALF_UP);

        List<Transaction> buyNowExp = new ArrayList<>(realExpenses);
        Transaction txNow = new Transaction();
        txNow.setAmount(amount);
        txNow.setDescription("[SIM-BUY-NOW] " + request.getDescription());
        txNow.setCategory("Shopping & Tech");
        txNow.setPaymentMethod("Credit");
        txNow.setUsername(username);
        txNow.setCreatedAt(LocalDateTime.now());
        buyNowExp.add(txNow);

        WhatIfResponse.ProjectionState buyNowState = buildProjectionState(
                compute3MonthCashFlowInternal(buyNowExp, realIncomes, realSubs),
                computeRiskScoreInternal(buyNowExp, realIncomes, realSubs)
        );
        WhatIfResponse.DiffSummary buyNowDiff = buildDiffSummary(baselineState, buyNowState, amount, "Buy Now", preferredCurrency);

        List<Transaction> buyLaterExp = new ArrayList<>(realExpenses);
        Transaction txLater = new Transaction();
        txLater.setAmount(amount);
        txLater.setDescription("[SIM-BUY-LATER] " + request.getDescription());
        txLater.setCategory("Shopping & Tech");
        txLater.setPaymentMethod("Credit");
        txLater.setUsername(username);
        txLater.setCreatedAt(LocalDateTime.now().plusMonths(delayMonths));
        buyLaterExp.add(txLater);

        WhatIfResponse.ProjectionState buyLaterState = buildProjectionState(
                compute3MonthCashFlowInternal(buyLaterExp, realIncomes, realSubs),
                computeRiskScoreInternal(buyLaterExp, realIncomes, realSubs)
        );
        WhatIfResponse.DiffSummary buyLaterDiff = buildDiffSummary(baselineState, buyLaterState, amount, "Buy in " + delayMonths + " Months", preferredCurrency);

        List<Transaction> buyEMIExp = new ArrayList<>(realExpenses);
        for (int m = 1; m <= Math.min(tenure, 3); m++) {
            Transaction txEMI = new Transaction();
            txEMI.setAmount(emiMonthly);
            txEMI.setDescription("[SIM-EMI-" + m + "] " + request.getDescription());
            txEMI.setCategory("Shopping & Tech");
            txEMI.setPaymentMethod("Credit");
            txEMI.setUsername(username);
            txEMI.setCreatedAt(LocalDateTime.now().plusMonths(m));
            buyEMIExp.add(txEMI);
        }

        WhatIfResponse.ProjectionState buyEMIState = buildProjectionState(
                compute3MonthCashFlowInternal(buyEMIExp, realIncomes, realSubs),
                computeRiskScoreInternal(buyEMIExp, realIncomes, realSubs)
        );
        WhatIfResponse.DiffSummary buyEMIDiff = buildDiffSummary(baselineState, buyEMIState, amount, "Buy via EMI (" + tenure + " mos)", preferredCurrency);

        String recText = String.format(
                "💡 Recommendation: Buy via EMI (%s/mo) yields the highest 3-month ending balance (%s vs %s Buy Now) while maintaining risk level at %s.",
                formatAmount(emiMonthly, preferredCurrency),
                formatAmount(buyEMIState.month3EndingBalance(), preferredCurrency),
                formatAmount(buyNowState.month3EndingBalance(), preferredCurrency),
                buyEMIState.riskLevel()
        );

        LocalizedMessage recMsg = new LocalizedMessage("sim.rec.emi", Map.of(
                "emi", (currencyConversionService != null) ? currencyConversionService.toMoneyParam(emiMonthly, preferredCurrency) : CurrencyConversionService.toMoney(emiMonthly, preferredCurrency),
                "emiEnd", (currencyConversionService != null) ? currencyConversionService.toMoneyParam(buyEMIState.month3EndingBalance(), preferredCurrency) : CurrencyConversionService.toMoney(buyEMIState.month3EndingBalance(), preferredCurrency),
                "buyNowEnd", (currencyConversionService != null) ? currencyConversionService.toMoneyParam(buyNowState.month3EndingBalance(), preferredCurrency) : CurrencyConversionService.toMoney(buyNowState.month3EndingBalance(), preferredCurrency),
                "risk", buyEMIState.riskLevel()
        ));

        LocalDateTime now = LocalDateTime.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();
        BigDecimal salary = realIncomes.stream()
                .filter(i -> i.getCreatedAt() != null && i.getCreatedAt().getMonthValue() == currentMonth && i.getCreatedAt().getYear() == currentYear)
                .map(Income::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (salary.compareTo(BigDecimal.ZERO) == 0) {
            salary = realIncomes.stream().map(Income::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal fixedExpenses = realSubs.stream().map(Subscription::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal minSavingsGoal = salary.multiply(BigDecimal.valueOf(0.20)).setScale(2, RoundingMode.HALF_UP);

        DecisionScenarioResult emiEvaluation = com.financeapp.service.DecisionScenarioEvaluator.evaluateEmiOption(
                amount,
                emiMonthly,
                salary,
                fixedExpenses,
                minSavingsGoal,
                BigDecimal.valueOf(0.30),
                com.financeapp.service.DecisionScenarioEvaluator.SavingsGoalContext.noGoal(),
                tenure,
                preferredCurrency
        );

        MultiScenarioWhatIfResponse.NamedScenarioResult resultA = new MultiScenarioWhatIfResponse.NamedScenarioResult("BUY_NOW", "⚡ Buy Now", buyNowState, buyNowDiff);
        MultiScenarioWhatIfResponse.NamedScenarioResult resultB = new MultiScenarioWhatIfResponse.NamedScenarioResult("BUY_IN_N_MONTHS", "⏳ Buy in " + delayMonths + " Months", buyLaterState, buyLaterDiff);
        MultiScenarioWhatIfResponse.NamedScenarioResult resultC = new MultiScenarioWhatIfResponse.NamedScenarioResult("BUY_VIA_EMI", "💳 Buy via EMI (" + tenure + " mos)", buyEMIState, buyEMIDiff);

        MultiScenarioWhatIfResponse response = new MultiScenarioWhatIfResponse(
                request.getDescription(), amount, delayMonths, tenure, emiMonthly, baselineState, resultA, resultB, resultC, recText, emiEvaluation, recMsg
        );
        if (whatIfSimulationService != null) {
            whatIfSimulationService.setActiveSimulation(username, response);
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/dashboard/what-if/active")
    public ResponseEntity<MultiScenarioWhatIfResponse> getActiveSimulation(Authentication authentication) {
        String username = authentication.getName();
        MultiScenarioWhatIfResponse active = whatIfSimulationService != null ? whatIfSimulationService.getActiveSimulation(username) : null;
        if (active != null) {
            return ResponseEntity.ok(active);
        }
        return ResponseEntity.noContent().build();
    }

    private GoalResponse mapGoalToResponse(SavingsGoal goal, String username, List<Income> incomes, List<Transaction> expenses) {
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

        String preferredCurrency = getPreferredCurrency(username);
        double estMonths = -1;
        String estCompletionDate = "Indefinite (Monthly Savings <= " + formatAmount(BigDecimal.ZERO, preferredCurrency) + ")";
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
                            : CurrencyConversionService.toMoney(BigDecimal.ZERO, preferredCurrency)
            ));
        }

        return new GoalResponse(goal.getId(), goal.getName(), target, current, remaining, Math.round(progressPct * 10.0) / 10.0, Math.round(estMonths * 10.0) / 10.0, estCompletionDate, goal.getTargetDate(), goal.getCreatedAt(), estCompletionMsg);
    }

    private WhatIfResponse.ProjectionState buildProjectionState(ThreeMonthCashFlowResponse cf, RiskScoreResponse risk) {
        BigDecimal end3M = cf.projections().isEmpty() ? cf.startingBalance() : cf.projections().get(cf.projections().size() - 1).endBalance();
        return new WhatIfResponse.ProjectionState(
                cf.startingBalance(), end3M, risk.totalRiskScore(), risk.riskLevel(), cf.projections()
        );
    }

    private WhatIfResponse.DiffSummary buildDiffSummary(WhatIfResponse.ProjectionState base, WhatIfResponse.ProjectionState sim, BigDecimal amount, String label) {
        return buildDiffSummary(base, sim, amount, label, "USD");
    }

    private WhatIfResponse.DiffSummary buildDiffSummary(WhatIfResponse.ProjectionState base, WhatIfResponse.ProjectionState sim, BigDecimal amount, String label, String preferredCurrency) {
        BigDecimal balImpact = sim.startingBalance().subtract(base.startingBalance());
        BigDecimal month3Impact = sim.month3EndingBalance().subtract(base.month3EndingBalance());
        int riskScoreImpact = sim.totalRiskScore() - base.totalRiskScore();
        boolean riskChanged = !base.riskLevel().equals(sim.riskLevel());
        String riskShift = base.riskLevel() + " → " + sim.riskLevel();

        String summary = String.format(
                "%s of %s impacts 3-month ending balance by %s (from %s to %s) and changes risk score by %+d pts (%s). ZERO DB rows modified.",
                label,
                formatAmount(amount, preferredCurrency),
                formatAmount(month3Impact, preferredCurrency),
                formatAmount(base.month3EndingBalance(), preferredCurrency),
                formatAmount(sim.month3EndingBalance(), preferredCurrency),
                riskScoreImpact,
                riskShift
        );

        LocalizedMessage impactMsg = new LocalizedMessage("sim.diff.summary", Map.of(
                "label", label,
                "amount", (currencyConversionService != null) ? currencyConversionService.toMoneyParam(amount, preferredCurrency) : CurrencyConversionService.toMoney(amount, preferredCurrency),
                "impact", (currencyConversionService != null) ? currencyConversionService.toMoneyParam(month3Impact, preferredCurrency) : CurrencyConversionService.toMoney(month3Impact, preferredCurrency),
                "from", (currencyConversionService != null) ? currencyConversionService.toMoneyParam(base.month3EndingBalance(), preferredCurrency) : CurrencyConversionService.toMoney(base.month3EndingBalance(), preferredCurrency),
                "to", (currencyConversionService != null) ? currencyConversionService.toMoneyParam(sim.month3EndingBalance(), preferredCurrency) : CurrencyConversionService.toMoney(sim.month3EndingBalance(), preferredCurrency),
                "points", String.format("%+d", riskScoreImpact),
                "shift", riskShift
        ));

        return new WhatIfResponse.DiffSummary(balImpact, month3Impact, riskScoreImpact, riskChanged, riskShift, summary, impactMsg);
    }

    private ThreeMonthCashFlowResponse compute3MonthCashFlowInternal(
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
                    String rawCat = t.getCategory();
                    String cat = (rawCat != null && CategoryService.isStandardCategory(rawCat) && !"Uncategorized".equalsIgnoreCase(rawCat))
                            ? CategoryService.normalizeCategory(rawCat)
                            : "Uncategorized";
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

    private RiskScoreResponse computeRiskScoreInternal(
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
