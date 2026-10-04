package com.financeapp.controller;

import com.financeapp.dto.ForecastResponse;
import com.financeapp.dto.MultiScenarioWhatIfResponse;
import com.financeapp.dto.RiskScoreResponse;
import com.financeapp.dto.ThreeMonthCashFlowResponse;
import com.financeapp.model.Income;
import com.financeapp.model.SavingsGoal;
import com.financeapp.model.Subscription;
import com.financeapp.model.Transaction;
import com.financeapp.repository.IncomeRepository;
import com.financeapp.repository.SavingsGoalRepository;
import com.financeapp.repository.SubscriptionRepository;
import com.financeapp.repository.TransactionRepository;
import com.financeapp.repository.UserRepository;
import com.financeapp.service.CurrencyConversionService;
import com.financeapp.service.RecommendationService;
import com.financeapp.service.ReportChartService;
import com.financeapp.service.ReportTranslationService;
import com.financeapp.service.WhatIfSimulationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private IncomeRepository incomeRepository;

    @Autowired(required = false)
    private SubscriptionRepository subscriptionRepository;

    @Autowired(required = false)
    private SavingsGoalRepository savingsGoalRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DashboardController dashboardController;

    @Autowired
    private CurrencyConversionService currencyConversionService;

    @Autowired
    private ReportChartService reportChartService;

    @Autowired
    private ReportTranslationService reportTranslationService;

    @Autowired(required = false)
    private WhatIfSimulationService whatIfSimulationService;

    @Autowired(required = false)
    private RecommendationService recommendationService;

    @GetMapping("/reports/monthly")
    public ResponseEntity<String> generateMonthlyReport(
            Authentication authentication,
            @RequestParam(value = "lang", required = false) String langParam
    ) {
        String username = authentication.getName();
        LocalDateTime now = LocalDateTime.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();

        // Determine language
        String lang = (langParam != null && !langParam.isBlank())
                ? langParam.trim().toLowerCase()
                : userRepository.findByUsername(username).map(u -> u.getPreferredLanguage()).orElse("en");
        if (lang == null || lang.isBlank()) lang = "en";

        var scriptInfo = reportTranslationService.getScriptInfo(lang);
        boolean isRtl = scriptInfo.isRtl();

        String reportDateStr = now.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.forLanguageTag(lang)));

        List<Transaction> expenses = transactionRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Income> incomes = incomeRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Subscription> subs = subscriptionRepository != null ? subscriptionRepository.findByUsernameAndIsActiveTrue(username) : Collections.emptyList();
        List<SavingsGoal> goals = savingsGoalRepository != null ? savingsGoalRepository.findByUsernameOrderByCreatedAtDesc(username) : Collections.emptyList();

        // 1. Net Balance
        BigDecimal totalIncome = incomes.stream().map(Income::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalExpenses = expenses.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalBalance = totalIncome.subtract(totalExpenses);

        // 2. Monthly Income
        BigDecimal monthlyIncome = incomes.stream()
                .filter(i -> i.getCreatedAt() != null && i.getCreatedAt().getMonthValue() == currentMonth && i.getCreatedAt().getYear() == currentYear)
                .map(Income::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. Monthly Expenses
        BigDecimal monthlyExpenses = expenses.stream()
                .filter(e -> e.getCreatedAt() != null && e.getCreatedAt().getMonthValue() == currentMonth && e.getCreatedAt().getYear() == currentYear)
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Savings Ratio
        BigDecimal savings = monthlyIncome.subtract(monthlyExpenses);
        double savingsRatio = monthlyIncome.compareTo(BigDecimal.ZERO) > 0
                ? savings.divide(monthlyIncome, 4, RoundingMode.HALF_UP).doubleValue() * 100.0 : 0.0;

        // 5. Category-wise Expense Breakdown
        Map<String, BigDecimal> categorySums = expenses.stream()
                .collect(Collectors.groupingBy(e -> e.getCategory() != null ? e.getCategory() : "Uncategorized",
                        Collectors.reducing(BigDecimal.ZERO, Transaction::getAmount, BigDecimal::add)));

        record CategoryShare(String category, BigDecimal amount, double percentage) {}
        List<CategoryShare> categoryBreakdown = new ArrayList<>();
        if (totalExpenses.compareTo(BigDecimal.ZERO) > 0) {
            for (Map.Entry<String, BigDecimal> entry : categorySums.entrySet()) {
                double pct = entry.getValue().divide(totalExpenses, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).doubleValue();
                categoryBreakdown.add(new CategoryShare(entry.getKey(), entry.getValue(), Math.round(pct * 10.0) / 10.0));
            }
        }
        categoryBreakdown.sort((a, b) -> b.amount().compareTo(a.amount()));

        // 6. Top Expense Category
        String topCategory = "N/A";
        BigDecimal topCatAmount = BigDecimal.ZERO;
        double topCatPct = 0.0;
        if (!categoryBreakdown.isEmpty()) {
            topCategory = categoryBreakdown.get(0).category();
            topCatAmount = categoryBreakdown.get(0).amount();
            topCatPct = categoryBreakdown.get(0).percentage();
        }

        // 7. Financial Risk Score
        ResponseEntity<RiskScoreResponse> riskRes = dashboardController.getRiskScore(authentication);
        RiskScoreResponse riskScore = riskRes != null ? riskRes.getBody() : null;

        // 8. Next-Month Expense Forecast
        ResponseEntity<ForecastResponse> forecastRes = dashboardController.getExpenseForecast(authentication);
        ForecastResponse forecast = forecastRes != null ? forecastRes.getBody() : null;

        // 9. Cash-Flow Projection Chart Data & Reserve Benchmark
        log.info("[REPORT-PIPELINE] [Step 9] Retrieving Cash-Flow data for user '{}'...", username);
        ThreeMonthCashFlowResponse cashFlow = null;
        try {
            ResponseEntity<ThreeMonthCashFlowResponse> cfRes = dashboardController.get3MonthCashFlow(authentication);
            if (cfRes != null && cfRes.getBody() != null) {
                cashFlow = cfRes.getBody();
                log.info("[REPORT-PIPELINE] [Step 9] Cash-Flow retrieved: startBal={}, income={}, recurring={}, projectionsCount={}",
                        cashFlow.startingBalance(), cashFlow.expectedMonthlyIncome(), cashFlow.recurringBills(),
                        cashFlow.projections() != null ? cashFlow.projections().size() : 0);
            } else {
                log.warn("[REPORT-PIPELINE] [Step 9] Cash-Flow response body is NULL for user '{}'", username);
            }
        } catch (Exception e) {
            log.error("[REPORT-PIPELINE] [Step 9] ERROR retrieving Cash-Flow data from DashboardController: {}", e.getMessage(), e);
        }

        BigDecimal reserveThreshold = BigDecimal.ZERO;
        if (cashFlow != null && cashFlow.projections() != null && !cashFlow.projections().isEmpty()) {
            BigDecimal totalProjected = cashFlow.projections().stream()
                    .map(p -> p.forecastedExpenses().add(p.recurringBills()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            reserveThreshold = totalProjected.multiply(new BigDecimal("1.10"));
            log.info("[REPORT-PIPELINE] [Step 9] Calculated emergency reserveThreshold={}", reserveThreshold);
        }

        String cashFlowChartSvg = "";
        try {
            log.info("[REPORT-PIPELINE] [Step 9] Invoking reportChartService.generateCashFlowProjectionChartSvg...");
            cashFlowChartSvg = reportChartService.generateCashFlowProjectionChartSvg(
                    cashFlow, reserveThreshold, username, currencyConversionService, lang, reportTranslationService
            );
            log.info("[REPORT-PIPELINE] [Step 9] Cash-Flow Chart SVG generated. Length: {} chars", cashFlowChartSvg.length());
        } catch (Exception e) {
            log.error("[REPORT-PIPELINE] [Step 9] ERROR generating Cash-Flow Projection Chart SVG: {}", e.getMessage(), e);
            cashFlowChartSvg = "<div style=\"padding: 24px; text-align: center; color: #ef4444;\">Error generating Cash-Flow chart: " + e.getMessage() + "</div>";
        }

        // 10. Active What-If Simulation
        log.info("[REPORT-PIPELINE] [Step 10] Checking active What-If simulation for user '{}'...", username);
        MultiScenarioWhatIfResponse activeSim = null;
        String scenarioComparisonChartSvg = "";
        if (whatIfSimulationService == null) {
            log.warn("[REPORT-PIPELINE] [Step 10] WhatIfSimulationService bean is NULL! Scenario Comparison Chart will be omitted.");
        } else {
            try {
                activeSim = whatIfSimulationService.getActiveSimulation(username);
                if (activeSim == null) {
                    log.info("[REPORT-PIPELINE] [Step 10] No active simulation found for user '{}'.", username);
                } else {
                    log.info("[REPORT-PIPELINE] [Step 10] Active simulation found: '{}'. Generating chart...", activeSim.scenarioDescription());
                    scenarioComparisonChartSvg = reportChartService.generateScenarioComparisonChartSvg(
                            activeSim, username, currencyConversionService, lang, reportTranslationService
                    );
                }
            } catch (Exception e) {
                log.error("[REPORT-PIPELINE] [Step 10] ERROR generating Scenario Comparison Chart SVG: {}", e.getMessage(), e);
            }
        }

        // Display currency information
        var currInfo = currencyConversionService.convertUsdForUser(BigDecimal.ONE, username);
        String symbol = currInfo.currencySymbol();
        String code = currInfo.currencyCode();

        // Localized Strings
        String tTitle = reportTranslationService.getMessage(lang, "report.title", "FinanceApp Monthly Report");
        String tMonthlyReport = reportTranslationService.getMessage(lang, "report.monthlyReport", "Monthly Executive Financial Report");
        String tDisplayCurrency = reportTranslationService.getMessage(lang, "report.displayCurrency", "Display Currency:");
        String tUser = reportTranslationService.getMessage(lang, "report.user", "User:");
        String tReportPeriod = reportTranslationService.getMessage(lang, "report.reportPeriod", "Report Period:");
        String tNetBalance = reportTranslationService.getMessage(lang, "report.netBalance", "Net Balance");
        String tMonthlyIncome = reportTranslationService.getMessage(lang, "report.monthlyIncome", "Monthly Income");
        String tMonthlyExpenses = reportTranslationService.getMessage(lang, "report.monthlyExpenses", "Monthly Expenses");
        String tSavingsRatio = reportTranslationService.getMessage(lang, "report.savingsRatio", "Savings Ratio");
        String tCategoryBreakdown = reportTranslationService.getMessage(lang, "report.categoryBreakdown", "Category-wise Expense Breakdown");
        String tNoExpenses = reportTranslationService.getMessage(lang, "report.noExpenses", "No expense transactions recorded for this period.");
        String tCategory = reportTranslationService.getMessage(lang, "report.category", "Category");
        String tSpent = reportTranslationService.getMessage(lang, "report.spent", "Spent");
        String tShareOfExpenses = reportTranslationService.getMessage(lang, "report.shareOfExpenses", "Share of Expenses");
        String tDistribution = reportTranslationService.getMessage(lang, "report.distribution", "Distribution");
        String tTopCategory = reportTranslationService.getMessage(lang, "report.topCategory", "Top Expense Category");
        String tTotalSpent = reportTranslationService.getMessage(lang, "report.totalSpent", "Total Spent:");
        String tOfTotalExpenses = reportTranslationService.getMessage(lang, "report.ofTotalExpenses", "of total expenses");
        String tRiskScore = reportTranslationService.getMessage(lang, "report.riskScore", "Financial Risk Score");
        String tRiskLevelSuffix = reportTranslationService.getMessage(lang, "report.riskLevelSuffix", "RISK");
        String tRiskModelDesc = reportTranslationService.getMessage(lang, "report.riskModelDesc", "Deterministic multi-factor risk assessment model");
        String tForecastTitle = reportTranslationService.getMessage(lang, "report.forecastTitle", "Next-Month Expense Forecast (OLS Linear Model)");
        String tSlope = reportTranslationService.getMessage(lang, "report.slope", "Slope (m)");
        String tProjectedExpense = reportTranslationService.getMessage(lang, "report.projectedExpense", "Projected Expense");
        String tTrendDirection = reportTranslationService.getMessage(lang, "report.trendDirection", "Trend Direction");
        String tNoForecast = reportTranslationService.getMessage(lang, "report.noForecast", "No forecast records available.");
        String tCashFlowChart = reportTranslationService.getMessage(lang, "report.cashFlowChart", "Cash-Flow Projection Chart");
        String tScenarioChart = reportTranslationService.getMessage(lang, "report.scenarioChart", "Scenario Comparison Chart");
        String tSavingsGoals = reportTranslationService.getMessage(lang, "report.savingsGoals", "Active Savings Goals");
        String tGoalName = reportTranslationService.getMessage(lang, "report.goalName", "Goal Name");
        String tTarget = reportTranslationService.getMessage(lang, "report.target", "Target");
        String tCurrentSaved = reportTranslationService.getMessage(lang, "report.currentSaved", "Current Saved");
        String tProgress = reportTranslationService.getMessage(lang, "report.progress", "Progress %");

        // Build HTML Report
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n")
            .append("<html lang=\"").append(lang).append("\" dir=\"").append(isRtl ? "rtl" : "ltr").append("\">\n")
            .append("<head>\n")
            .append("<meta charset=\"UTF-8\">\n")
            .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
            .append("<title>").append(tTitle).append(" - ").append(reportDateStr).append("</title>\n")
            .append("<link rel=\"preconnect\" href=\"https://fonts.googleapis.com\">\n")
            .append("<link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin>\n")
            .append("<link href=\"").append(scriptInfo.googleFontUrl()).append("\" rel=\"stylesheet\">\n")
            .append("<style>\n")
            .append("body { font-family: ").append(scriptInfo.fontCss()).append("; background: #090d16; color: #f1f5f9; padding: 40px 20px; margin: 0; direction: ").append(isRtl ? "rtl" : "ltr").append("; text-align: ").append(isRtl ? "right" : "left").append("; }\n")
            .append(".container { max-width: 860px; margin: 0 auto; background: #131b2e; border: 1px solid rgba(255,255,255,0.1); border-radius: 14px; padding: 36px; box-shadow: 0 25px 50px -12px rgba(0,0,0,0.6); }\n")
            .append(".header { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid rgba(255,255,255,0.1); padding-bottom: 20px; margin-bottom: 26px; }\n")
            .append(".header-start { text-align: ").append(isRtl ? "right" : "left").append("; }\n")
            .append(".header-end { text-align: ").append(isRtl ? "left" : "right").append("; }\n")
            .append(".logo { font-size: 24px; font-weight: 800; color: #6366f1; letter-spacing: -0.5px; }\n")
            .append(".grid-4 { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 24px; }\n")
            .append(".grid-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; margin-bottom: 24px; }\n")
            .append(".card { background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.08); padding: 18px; border-radius: 10px; }\n")
            .append(".label { font-size: 11px; color: #94a3b8; text-transform: uppercase; font-weight: 700; letter-spacing: 0.5px; margin-bottom: 6px; }\n")
            .append(".val { font-size: 20px; font-weight: 800; }\n")
            .append(".section-title { font-size: 14px; text-transform: uppercase; font-weight: 800; letter-spacing: 0.5px; margin-top: 28px; margin-bottom: 12px; display: flex; align-items: center; gap: 8px; }\n")
            .append(".title-purple { color: #818cf8; }\n")
            .append(".title-blue { color: #38bdf8; }\n")
            .append(".title-green { color: #34d399; }\n")
            .append(".title-amber { color: #fbbf24; }\n")
            .append("table { width: 100%; border-collapse: collapse; margin-top: 8px; margin-bottom: 16px; font-size: 13px; }\n")
            .append("th, td { text-align: ").append(isRtl ? "right" : "left").append("; padding: 10px 12px; border-bottom: 1px solid rgba(255,255,255,0.07); }\n")
            .append("th { color: #94a3b8; text-transform: uppercase; font-size: 11px; font-weight: 700; letter-spacing: 0.5px; background: rgba(255,255,255,0.02); }\n")
            .append(".chart-box { margin-top: 8px; margin-bottom: 20px; border-radius: 10px; overflow: hidden; }\n")
            .append(".bar-track { width: 100%; height: 6px; background: rgba(255,255,255,0.08); border-radius: 999px; overflow: hidden; margin-top: 4px; }\n")
            .append(".bar-fill { height: 100%; background: linear-gradient(90deg, #6366f1, #818cf8); border-radius: 999px; }\n")
            .append(".badge { padding: 4px 10px; border-radius: 4px; font-size: 11px; font-weight: 700; display: inline-block; }\n")
            .append("@media print { body { background: #fff; color: #000; padding: 0; } .container { background: #fff; color: #000; border: none; box-shadow: none; } .card { border: 1px solid #ccc; background: #fafafa; } th, td { border-bottom: 1px solid #ddd; } }\n")
            .append("</style>\n</head>\n<body>\n")
            .append("<div class=\"container\">\n");

        // Header
        html.append("<div class=\"header\">\n")
            .append("<div class=\"header-start\">\n")
            .append("<div class=\"logo\">💰 FinanceApp</div>\n")
            .append("<div style=\"font-size: 13.5px; color: #94a3b8; margin-top: 4px;\">")
            .append(tMonthlyReport).append(" (").append(tDisplayCurrency).append(" <strong>").append(code).append(" ").append(symbol).append("</strong>)</div>\n")
            .append("</div>\n")
            .append("<div class=\"header-end\">\n")
            .append("<div style=\"font-weight: 700; font-size: 15px; color: #f1f5f9;\">").append(tUser).append(" ").append(username).append("</div>\n")
            .append("<div style=\"font-size: 12px; color: #94a3b8; margin-top: 2px;\">").append(tReportPeriod).append(" ").append(reportDateStr).append("</div>\n")
            .append("</div>\n")
            .append("</div>\n");

        // ── IN-ORDER SECTIONS 1, 2, 3, 4: Net Balance, Monthly Income, Monthly Expenses, Savings Ratio ──
        html.append("<div class=\"grid-4\">\n")
            // 1. Net Balance
            .append("<div class=\"card\">\n")
            .append("<div class=\"label\">").append(tNetBalance).append("</div>\n")
            .append("<div class=\"val\" style=\"color: #34d399;\">").append(currencyConversionService.formatUsdForUser(totalBalance, username)).append("</div>\n")
            .append("</div>\n")
            // 2. Monthly Income
            .append("<div class=\"card\">\n")
            .append("<div class=\"label\">").append(tMonthlyIncome).append("</div>\n")
            .append("<div class=\"val\" style=\"color: #34d399;\">").append(currencyConversionService.formatUsdForUser(monthlyIncome, username)).append("</div>\n")
            .append("</div>\n")
            // 3. Monthly Expenses
            .append("<div class=\"card\">\n")
            .append("<div class=\"label\">").append(tMonthlyExpenses).append("</div>\n")
            .append("<div class=\"val\" style=\"color: #f87171;\">").append(currencyConversionService.formatUsdForUser(monthlyExpenses, username)).append("</div>\n")
            .append("</div>\n")
            // 4. Savings Ratio
            .append("<div class=\"card\">\n")
            .append("<div class=\"label\">").append(tSavingsRatio).append("</div>\n")
            .append("<div class=\"val\" style=\"color: #60a5fa;\">").append(String.format(Locale.US, "%.1f%%", savingsRatio)).append("</div>\n")
            .append("</div>\n")
            .append("</div>\n");

        // ── SECTION 5: Category-wise Expense Breakdown ──
        html.append("<div class=\"section-title title-purple\">🏷️ ").append(tCategoryBreakdown).append("</div>\n");
        if (categoryBreakdown.isEmpty()) {
            html.append("<div class=\"card\" style=\"text-align: center; color: #94a3b8; padding: 20px;\">").append(tNoExpenses).append("</div>\n");
        } else {
            html.append("<table>\n<thead><tr>")
                .append("<th>").append(tCategory).append("</th>")
                .append("<th>").append(tSpent).append(" (").append(symbol).append(")</th>")
                .append("<th>").append(tShareOfExpenses).append("</th>")
                .append("<th style=\"width: 30%;\">").append(tDistribution).append("</th>")
                .append("</tr></thead>\n<tbody>\n");

            for (CategoryShare cs : categoryBreakdown) {
                String catDisplayName = reportTranslationService.getCategoryDisplayName(lang, cs.category());
                html.append("<tr>\n")
                    .append("<td style=\"font-weight: 600;\">").append(catDisplayName).append("</td>\n")
                    .append("<td style=\"font-weight: 700; color: #f1f5f9;\">").append(currencyConversionService.formatUsdForUser(cs.amount(), username)).append("</td>\n")
                    .append("<td style=\"color: #94a3b8; font-weight: 600;\">").append(String.format(Locale.US, "%.1f%%", cs.percentage())).append("</td>\n")
                    .append("<td><div class=\"bar-track\"><div class=\"bar-fill\" style=\"width: ").append(Math.min(Math.max(cs.percentage(), 2.0), 100.0)).append("%;\"></div></div></td>\n")
                    .append("</tr>\n");
            }
            html.append("</tbody>\n</table>\n");
        }

        // ── SECTIONS 6 & 7: Top Expense Category & Financial Risk Score ──
        String topCatDisplayName = reportTranslationService.getCategoryDisplayName(lang, topCategory);
        html.append("<div class=\"grid-2\">\n")
            // 6. Top Expense Category
            .append("<div class=\"card\">\n")
            .append("<div class=\"label\">").append(tTopCategory).append("</div>\n")
            .append("<div style=\"font-size: 18px; font-weight: 800; color: #f1f5f9; margin-top: 4px;\">🏷️ ").append(topCatDisplayName).append("</div>\n")
            .append("<div style=\"font-size: 12px; color: #94a3b8; margin-top: 4px;\">")
            .append(tTotalSpent).append(" <strong>").append(currencyConversionService.formatUsdForUser(topCatAmount, username)).append("</strong> (")
            .append(String.format(Locale.US, "%.1f%%", topCatPct)).append(" ").append(tOfTotalExpenses).append(")</div>\n")
            .append("</div>\n")
            // 7. Financial Risk Score
            .append("<div class=\"card\">\n")
            .append("<div class=\"label\">").append(tRiskScore).append("</div>\n")
            .append("<div style=\"font-size: 18px; font-weight: 800; color: #e879f9; margin-top: 4px;\">🛡️ ")
            .append(riskScore != null ? riskScore.totalRiskScore() : 0).append(" / 100 (")
            .append(riskScore != null ? riskScore.riskLevel() : "LOW").append(" ").append(tRiskLevelSuffix).append(")</div>\n")
            .append("<div style=\"font-size: 12px; color: #94a3b8; margin-top: 4px;\">").append(tRiskModelDesc).append("</div>\n")
            .append("</div>\n")
            .append("</div>\n");

        // ── SECTION 8: Next-Month Expense Forecast ──
        html.append("<div class=\"section-title title-blue\">").append(tForecastTitle).append("</div>\n")
            .append("<table>\n<thead><tr>")
            .append("<th>").append(tCategory).append("</th>")
            .append("<th>").append(tSlope).append("</th>")
            .append("<th>").append(tProjectedExpense).append(" (").append(symbol).append(")</th>")
            .append("<th>").append(tTrendDirection).append("</th>")
            .append("</tr></thead>\n<tbody>\n");

        if (forecast != null && forecast.categoryForecasts() != null && !forecast.categoryForecasts().isEmpty()) {
            for (ForecastResponse.CategoryForecast cf : forecast.categoryForecasts()) {
                String catName = reportTranslationService.getCategoryDisplayName(lang, cf.category());
                String trend = (cf.trendMessage() != null)
                        ? reportTranslationService.resolveMessage(lang, cf.trendMessage())
                        : cf.trendText();
                html.append("<tr>\n")
                    .append("<td style=\"font-weight: 600;\">").append(catName).append("</td>\n")
                    .append("<td>").append(cf.slope()).append("</td>\n")
                    .append("<td style=\"color: #f87171; font-weight: 700;\">").append(currencyConversionService.formatUsdForUser(cf.projectedAmount(), username)).append("</td>\n")
                    .append("<td>").append(trend).append("</td>\n")
                    .append("</tr>\n");
            }
        } else {
            html.append("<tr><td colspan=\"4\" style=\"text-align: center; color: #94a3b8; padding: 16px;\">").append(tNoForecast).append("</td></tr>\n");
        }
        html.append("</tbody>\n</table>\n");

        String disclaimerText = null;
        if (forecast != null) {
            if (forecast.disclaimerMessage() != null) {
                disclaimerText = reportTranslationService.resolveMessage(lang, forecast.disclaimerMessage());
            } else if (forecast.disclaimer() != null) {
                disclaimerText = forecast.disclaimer();
            }
        }
        if (disclaimerText != null && !disclaimerText.isBlank()) {
            html.append("<div style=\"margin-top: 8px; margin-bottom: 18px; font-size: 11px; color: #94a3b8; font-style: italic;\">")
                .append(disclaimerText)
                .append("</div>\n");
        }

        // ── SECTION 9: Cash-Flow Projection Chart ──
        log.info("[REPORT-PIPELINE] Embedding Step 9: Cash-Flow Projection Chart into HTML. SVG length={}",
                cashFlowChartSvg != null ? cashFlowChartSvg.length() : 0);
        html.append("<div class=\"section-title title-purple\">").append(tCashFlowChart).append("</div>\n")
            .append("<div class=\"chart-box\">\n")
            .append(cashFlowChartSvg)
            .append("</div>\n");

        // ── SECTION 10: Scenario Comparison Chart (only if active simulation exists) ──
        if (activeSim != null) {
            log.info("[REPORT-PIPELINE] Embedding Step 10: Scenario Comparison Chart into HTML. SVG length={}",
                    scenarioComparisonChartSvg.length());
            html.append("<div class=\"section-title title-amber\">").append(tScenarioChart).append("</div>\n")
                .append("<div class=\"chart-box\">\n")
                .append(scenarioComparisonChartSvg)
                .append("</div>\n");
        } else {
            log.info("[REPORT-PIPELINE] Skipping Step 10: Scenario Comparison Chart (no active simulation).");
        }

        // ── SECTION 11: Savings Goals ──
        if (!goals.isEmpty()) {
            log.info("[REPORT-PIPELINE] Embedding Step 11: Active Savings Goals (count={})", goals.size());
            html.append("<div class=\"section-title title-green\">").append(tSavingsGoals).append("</div>\n")
                .append("<table>\n<thead><tr>")
                .append("<th>").append(tGoalName).append("</th>")
                .append("<th>").append(tTarget).append(" (").append(symbol).append(")</th>")
                .append("<th>").append(tCurrentSaved).append(" (").append(symbol).append(")</th>")
                .append("<th>").append(tProgress).append("</th>")
                .append("</tr></thead>\n<tbody>\n");

            for (SavingsGoal g : goals) {
                double pct = g.getTargetAmount().compareTo(BigDecimal.ZERO) > 0
                        ? g.getCurrentAmount().divide(g.getTargetAmount(), 4, RoundingMode.HALF_UP).doubleValue() * 100.0 : 0.0;
                html.append("<tr>\n")
                    .append("<td style=\"font-weight: 600;\">").append(g.getName()).append("</td>\n")
                    .append("<td>").append(currencyConversionService.formatUsdForUser(g.getTargetAmount(), username)).append("</td>\n")
                    .append("<td>").append(currencyConversionService.formatUsdForUser(g.getCurrentAmount(), username)).append("</td>\n")
                    .append("<td style=\"color: #34d399; font-weight: 700;\">").append(String.format(Locale.US, "%.1f%%", pct)).append("</td>\n")
                    .append("</tr>\n");
            }
            html.append("</tbody>\n</table>\n");
        } else {
            log.info("[REPORT-PIPELINE] Skipping Step 11: Savings Goals (no active goals).");
        }

        // Footer
        String footerTemplate = reportTranslationService.getMessage(lang, "report.footer", "Report generated automatically by FinanceApp on {{date}}. Confidential.");
        String formattedDate = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String footerText = footerTemplate.replace("{{date}}", formattedDate).replace("{date}", formattedDate);

        html.append("<div style=\"margin-top: 40px; padding-top: 16px; border-top: 1px solid rgba(255,255,255,0.1); text-align: center; font-size: 11px; color: #64748b;\">\n")
            .append(footerText).append("\n")
            .append("</div>\n")
            .append("</div>\n</body>\n</html>");

        String finalHtml = html.toString();
        log.info("[REPORT-PIPELINE] Report generation completed for user '{}', lang='{}'. Total length: {} characters",
                username, lang, finalHtml.length());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"FinanceApp_Monthly_Report_" + lang + ".html\"")
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(finalHtml);
    }
}
