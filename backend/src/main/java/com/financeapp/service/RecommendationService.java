package com.financeapp.service;

import com.financeapp.dto.FinancialRecommendationResponse;
import com.financeapp.dto.GoalResponse;
import com.financeapp.dto.LocalizedMessage;
import com.financeapp.dto.MoneyParam;
import com.financeapp.dto.RiskScoreResponse;
import com.financeapp.dto.ThreeMonthCashFlowResponse;
import com.financeapp.model.FinancialScheme;
import com.financeapp.model.Subscription;
import com.financeapp.model.Transaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class RecommendationService {

    private final CurrencyConversionService currencyConversionService;
    private final SchemeKnowledgeBase schemeKnowledgeBase;

    public RecommendationService() {
        this.currencyConversionService = null;
        this.schemeKnowledgeBase = new SchemeKnowledgeBase();
    }

    @org.springframework.beans.factory.annotation.Autowired
    public RecommendationService(CurrencyConversionService currencyConversionService, SchemeKnowledgeBase schemeKnowledgeBase) {
        this.currencyConversionService = currencyConversionService;
        this.schemeKnowledgeBase = schemeKnowledgeBase != null ? schemeKnowledgeBase : new SchemeKnowledgeBase();
    }

    private String formatMoney(BigDecimal usdAmount, String currencyCode) {
        if (currencyConversionService != null) {
            return currencyConversionService.formatAmount(usdAmount, currencyCode);
        }
        return CurrencyConversionService.formatMoney(usdAmount, currencyCode);
    }

    private MoneyParam toMoney(BigDecimal usdAmount, String currencyCode) {
        if (currencyConversionService != null) {
            return currencyConversionService.toMoneyParam(usdAmount, currencyCode);
        }
        return CurrencyConversionService.toMoney(usdAmount, currencyCode);
    }

    public FinancialRecommendationResponse evaluateRecommendations(
            ThreeMonthCashFlowResponse cashFlow,
            RiskScoreResponse riskScore,
            List<GoalResponse> goals
    ) {
        return evaluateRecommendations(cashFlow, riskScore, goals, "USD", Collections.emptyList(), Collections.emptyList(), false, false);
    }

    public FinancialRecommendationResponse evaluateRecommendations(
            ThreeMonthCashFlowResponse cashFlow,
            RiskScoreResponse riskScore,
            List<GoalResponse> goals,
            String preferredCurrency
    ) {
        return evaluateRecommendations(cashFlow, riskScore, goals, preferredCurrency, Collections.emptyList(), Collections.emptyList(), false, false);
    }

    public FinancialRecommendationResponse evaluateRecommendations(
            ThreeMonthCashFlowResponse cashFlow,
            RiskScoreResponse riskScore,
            List<GoalResponse> goals,
            String preferredCurrency,
            List<Transaction> transactions,
            List<Subscription> subscriptions,
            boolean hasHealthInsurance,
            boolean hasLifeInsurance
    ) {
        String currency = (preferredCurrency != null && !preferredCurrency.isBlank()) ? preferredCurrency : "USD";
        boolean isINR = "INR".equalsIgnoreCase(currency);
        List<FinancialRecommendationResponse.RecommendationItem> recList = new ArrayList<>();

        BigDecimal startBalance = cashFlow != null ? cashFlow.startingBalance() : BigDecimal.ZERO;
        BigDecimal expectedIncome = cashFlow != null ? cashFlow.expectedMonthlyIncome() : BigDecimal.ZERO;
        BigDecimal recurringBills = cashFlow != null ? cashFlow.recurringBills() : BigDecimal.ZERO;

        // 1. Emergency Reserve Threshold (3 Months Outflow Benchmark)
        BigDecimal monthlyOutflow = recurringBills;
        if (cashFlow != null && cashFlow.projections() != null && !cashFlow.projections().isEmpty()) {
            monthlyOutflow = monthlyOutflow.add(cashFlow.projections().get(0).forecastedExpenses());
        }
        BigDecimal reserveThreshold = monthlyOutflow.multiply(BigDecimal.valueOf(3)).setScale(2, RoundingMode.HALF_UP);

        boolean isBelowReserve = false;
        BigDecimal lowestProjectedBalance = startBalance;

        if (cashFlow != null && cashFlow.projections() != null) {
            for (ThreeMonthCashFlowResponse.MonthlyCashFlowProjection p : cashFlow.projections()) {
                if (p.endBalance().compareTo(lowestProjectedBalance) < 0) {
                    lowestProjectedBalance = p.endBalance();
                }
            }
        }

        if (startBalance.compareTo(reserveThreshold) < 0 || lowestProjectedBalance.compareTo(reserveThreshold) < 0) {
            isBelowReserve = true;
            String actionable = isINR
                    ? "Prioritize building this reserve using low-risk liquid options such as a Bank Recurring Deposit (RD) or high-liquidity savings before allocating to long-term commitments. Check current terms on the official site."
                    : "Prioritize building this reserve using low-risk liquid options such as a high-yield savings account or short-term certificate of deposit before committing to illiquid assets.";
            String officialUrl = isINR ? schemeKnowledgeBase.getBankRD().officialUrl() : null;

            LocalizedMessage titleMsg = new LocalizedMessage("rec.reserve.buffer.title");
            LocalizedMessage detailMsg = new LocalizedMessage("rec.reserve.below.detail", Map.of(
                    "balance", toMoney(lowestProjectedBalance, currency),
                    "threshold", toMoney(reserveThreshold, currency)
            ));
            LocalizedMessage actionMsg = new LocalizedMessage(isINR ? "rec.reserve.below.action.inr" : "rec.reserve.below.action.default");

            recList.add(new FinancialRecommendationResponse.RecommendationItem(
                    "Emergency Reserve Buffer",
                    "HIGH",
                    String.format("Your projected balance (%s) drops below your recommended 3-month emergency reserve threshold (%s). Building a liquid emergency reserve protects against unforeseen financial shocks.",
                            formatMoney(lowestProjectedBalance, currency),
                            formatMoney(reserveThreshold, currency)),
                    actionable,
                    "Savings",
                    officialUrl,
                    titleMsg,
                    detailMsg,
                    actionMsg
            ));
        } else {
            LocalizedMessage titleMsg = new LocalizedMessage("rec.reserve.buffer.title");
            LocalizedMessage detailMsg = new LocalizedMessage("rec.reserve.ok.detail", Map.of(
                    "balance", toMoney(startBalance, currency),
                    "threshold", toMoney(reserveThreshold, currency)
            ));
            LocalizedMessage actionMsg = new LocalizedMessage("rec.reserve.ok.action");

            recList.add(new FinancialRecommendationResponse.RecommendationItem(
                    "Emergency Reserve Buffer",
                    "LOW",
                    String.format("Your starting balance (%s) comfortably exceeds your 3-month emergency reserve threshold (%s).",
                            formatMoney(startBalance, currency),
                            formatMoney(reserveThreshold, currency)),
                    "Maintain current reserve levels and direct surplus monthly cash flow into long-term savings or investments.",
                    "Savings",
                    null,
                    titleMsg,
                    detailMsg,
                    actionMsg
            ));
        }

        // 2. Financial Risk Profile
        String riskLevel = riskScore != null ? riskScore.riskLevel() : "LOW";
        int totalScore = riskScore != null ? riskScore.totalRiskScore() : 0;

        LocalizedMessage riskTitleMsg = new LocalizedMessage("rec.risk.profile.title");
        if ("HIGH".equals(riskLevel)) {
            recList.add(new FinancialRecommendationResponse.RecommendationItem(
                    "Financial Risk Profile",
                    "HIGH",
                    String.format("Total financial risk score is High (%d/100). High expense growth or heavy fixed obligations threaten cash flow stability.", totalScore),
                    "Audit recurring obligations and pause high-cost discretionary spending to reduce risk escalation.",
                    "Savings",
                    null,
                    riskTitleMsg,
                    new LocalizedMessage("rec.risk.high.detail", Map.of("score", totalScore)),
                    new LocalizedMessage("rec.risk.high.action")
            ));
        } else if ("MEDIUM".equals(riskLevel)) {
            recList.add(new FinancialRecommendationResponse.RecommendationItem(
                    "Financial Risk Profile",
                    "MEDIUM",
                    String.format("Financial risk score is Medium (%d/100). Current spending patterns warrant proactive management.", totalScore),
                    "Aim to maintain a savings ratio above 30% to build a resilient buffer.",
                    "Savings",
                    null,
                    riskTitleMsg,
                    new LocalizedMessage("rec.risk.medium.detail", Map.of("score", totalScore)),
                    new LocalizedMessage("rec.risk.medium.action")
            ));
        } else {
            recList.add(new FinancialRecommendationResponse.RecommendationItem(
                    "Financial Risk Profile",
                    "LOW",
                    String.format("Financial risk score is Low (%d/100). Savings ratio and fixed commitments remain within optimal benchmarks.", totalScore),
                    "Continue disciplined monthly budgeting to maintain stable low-risk financial standing.",
                    "Savings",
                    null,
                    riskTitleMsg,
                    new LocalizedMessage("rec.risk.low.detail", Map.of("score", totalScore)),
                    new LocalizedMessage("rec.risk.low.action")
            ));
        }

        // 3. Gap-Detection Rule 1.a: Health / Medical / Emergency Insurance Gap
        if (!hasHealthInsurance && goals != null) {
            Optional<GoalResponse> medicalGoal = goals.stream()
                    .filter(g -> isMedicalOrEmergencyGoal(g.name()))
                    .findFirst();

            if (medicalGoal.isPresent()) {
                boolean hasInsuranceTx = hasInsuranceTransactionInLast12Months(transactions);
                if (!hasInsuranceTx) {
                    String actionable = isINR
                            ? "Explore comprehensive health cover (e.g. Ayushman Bharat PM-JAY or individual health policies) to protect emergency reserves. Check current terms on the official site."
                            : "Explore dedicated health insurance options to safeguard your emergency cash reserves against major unexpected medical events.";
                    String officialUrl = isINR ? schemeKnowledgeBase.getPMJAY().officialUrl() : null;

                    recList.add(new FinancialRecommendationResponse.RecommendationItem(
                            "Health Insurance Coverage Gap",
                            "HIGH",
                            String.format("No insurance premium found in your recorded transactions for active emergency/medical goal '%s'. Relying solely on cash savings for medical events risks depleting your liquid reserves rapidly.",
                                    medicalGoal.get().name()),
                            actionable,
                            "Insurance",
                            officialUrl,
                            new LocalizedMessage("rec.insurance.gap.title"),
                            new LocalizedMessage("rec.insurance.gap.detail", Map.of("goalName", medicalGoal.get().name())),
                            new LocalizedMessage(isINR ? "rec.insurance.gap.action.inr" : "rec.insurance.gap.action.default")
                    ));
                }
            }
        }

        // 4. Future-Expense Planning (Rule 2)
        BigDecimal netSavingsRate = expectedIncome.subtract(monthlyOutflow);
        if (goals != null) {
            for (GoalResponse goal : goals) {
                Long monthsRemaining = getMonthsRemaining(goal);
                if (monthsRemaining != null && monthsRemaining > 0 && monthsRemaining <= 12) {
                    BigDecimal remainingAmount = goal.remainingAmount();
                    if (remainingAmount != null && remainingAmount.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal monthlyNeeded = remainingAmount.divide(BigDecimal.valueOf(monthsRemaining), 2, RoundingMode.HALF_UP);
                        if (netSavingsRate.compareTo(monthlyNeeded) >= 0) {
                            String actionable = isINR
                                    ? String.format("Set aside %s monthly in a dedicated Bank Recurring Deposit (RD) to ensure funds are ready when due. Check current terms on the official site.",
                                    formatMoney(monthlyNeeded, currency))
                                    : String.format("Set aside %s monthly in a dedicated liquid savings account to guarantee funds are ready when due.",
                                    formatMoney(monthlyNeeded, currency));
                            String officialUrl = isINR ? schemeKnowledgeBase.getBankRD().officialUrl() : null;

                            recList.add(new FinancialRecommendationResponse.RecommendationItem(
                                    "Future Expense Planning: " + goal.name(),
                                    "MEDIUM",
                                    String.format("Upcoming target '%s' requires %s/mo across %d remaining months. Your current monthly surplus (%s/mo) comfortably covers this allocation.",
                                            goal.name(),
                                            formatMoney(monthlyNeeded, currency),
                                            monthsRemaining,
                                            formatMoney(netSavingsRate, currency)),
                                    actionable,
                                    "Savings",
                                    officialUrl,
                                    new LocalizedMessage("rec.goal.planning.title", Map.of("goalName", goal.name())),
                                    new LocalizedMessage("rec.goal.planning.detail", Map.of(
                                            "goalName", goal.name(),
                                            "monthlyNeeded", toMoney(monthlyNeeded, currency),
                                            "months", monthsRemaining,
                                            "surplus", toMoney(netSavingsRate, currency)
                                    )),
                                    new LocalizedMessage(isINR ? "rec.goal.planning.action.inr" : "rec.goal.planning.action.default", Map.of(
                                            "monthlyNeeded", toMoney(monthlyNeeded, currency)
                                    ))
                            ));
                        } else {
                            BigDecimal shortfall = monthlyNeeded.subtract(netSavingsRate.max(BigDecimal.ZERO));
                            recList.add(new FinancialRecommendationResponse.RecommendationItem(
                                    "Future Expense Shortfall: " + goal.name(),
                                    "HIGH",
                                    String.format("Upcoming target '%s' requires %s/mo across %d remaining months, but your current monthly surplus is only %s/mo. You face an estimated monthly shortfall of %s.",
                                            goal.name(),
                                            formatMoney(monthlyNeeded, currency),
                                            monthsRemaining,
                                            formatMoney(netSavingsRate, currency),
                                            formatMoney(shortfall, currency)),
                                    String.format("Review and reduce discretionary expenses or extend the target timeline to bridge the %s/mo gap.",
                                            formatMoney(shortfall, currency)),
                                    "Savings",
                                    null,
                                    new LocalizedMessage("rec.goal.shortfall.title", Map.of("goalName", goal.name())),
                                    new LocalizedMessage("rec.goal.shortfall.detail", Map.of(
                                            "goalName", goal.name(),
                                            "monthlyNeeded", toMoney(monthlyNeeded, currency),
                                            "months", monthsRemaining,
                                            "surplus", toMoney(netSavingsRate, currency),
                                            "shortfall", toMoney(shortfall, currency)
                                    )),
                                    new LocalizedMessage("rec.goal.shortfall.action", Map.of(
                                            "shortfall", toMoney(shortfall, currency)
                                    ))
                            ));
                        }
                    }
                }
            }
        }

        // 5. Goal Horizon Guidance (Rule 1.c) - Short-term vs Long-term safe options
        if (goals != null && !goals.isEmpty()) {
            GoalResponse topGoal = goals.get(0);
            Long monthsRemaining = getMonthsRemaining(topGoal);

            // Active goal pace / deficit card
            if (netSavingsRate.compareTo(BigDecimal.ZERO) > 0) {
                recList.add(new FinancialRecommendationResponse.RecommendationItem(
                        String.format("Savings Goal: %s", topGoal.name()),
                        "LOW",
                        String.format("With a monthly net savings rate of %s/mo, goal '%s' is estimated for completion by %s.",
                                formatMoney(netSavingsRate, currency),
                                topGoal.name(),
                                topGoal.estimatedCompletionDate()),
                        String.format("Maintain monthly contributions of %s to stay on track for estimated completion by %s.",
                                formatMoney(netSavingsRate, currency),
                                topGoal.estimatedCompletionDate()),
                        "Savings",
                        null,
                        new LocalizedMessage("rec.goal.ontrack.title", Map.of("goalName", topGoal.name())),
                        new LocalizedMessage("rec.goal.ontrack.detail", Map.of(
                                "surplus", toMoney(netSavingsRate, currency),
                                "goalName", topGoal.name(),
                                "completionDate", topGoal.estimatedCompletionDate() != null ? topGoal.estimatedCompletionDate() : ""
                        )),
                        new LocalizedMessage("rec.goal.ontrack.action", Map.of(
                                "surplus", toMoney(netSavingsRate, currency),
                                "completionDate", topGoal.estimatedCompletionDate() != null ? topGoal.estimatedCompletionDate() : ""
                        ))
                ));
            } else {
                recList.add(new FinancialRecommendationResponse.RecommendationItem(
                        String.format("Savings Goal: %s", topGoal.name()),
                        "HIGH",
                        String.format("Current monthly spending exceeds income (%s/mo deficit), halting progress on active goal '%s'.",
                                formatMoney(netSavingsRate.abs(), currency),
                                topGoal.name()),
                        String.format("Reduce monthly discretionary expenses to resolve the %s deficit and resume contributions toward '%s'.",
                                formatMoney(netSavingsRate.abs(), currency),
                                topGoal.name()),
                        "Savings",
                        null,
                        new LocalizedMessage("rec.goal.deficit.title", Map.of("goalName", topGoal.name())),
                        new LocalizedMessage("rec.goal.deficit.detail", Map.of(
                                "deficit", toMoney(netSavingsRate.abs(), currency),
                                "goalName", topGoal.name()
                        )),
                        new LocalizedMessage("rec.goal.deficit.action", Map.of(
                                "deficit", toMoney(netSavingsRate.abs(), currency),
                                "goalName", topGoal.name()
                        ))
                ));
            }

            // Long-term vehicle guidance (> 36 months)
            if (monthsRemaining != null && monthsRemaining > 36) {
                String actionable = isINR
                        ? "Consider government-backed long-term compounding instruments such as Public Provident Fund (PPF) or National Pension System (NPS). Check current terms on the official site."
                        : "Consider long-term diversified retirement and compounding investment accounts over extended horizons.";
                String officialUrl = isINR ? schemeKnowledgeBase.getPPF().officialUrl() : null;

                recList.add(new FinancialRecommendationResponse.RecommendationItem(
                        "Long-Term Wealth Vehicle: " + topGoal.name(),
                        "LOW",
                        String.format("Goal '%s' has an extended horizon of over 3 years (~%d months). Long horizons can benefit from disciplined compounded savings vehicles.",
                                topGoal.name(), monthsRemaining),
                        actionable,
                        "Pension",
                        officialUrl,
                        new LocalizedMessage("rec.goal.longterm.title", Map.of("goalName", topGoal.name())),
                        new LocalizedMessage("rec.goal.longterm.detail", Map.of(
                                "goalName", topGoal.name(),
                                "months", monthsRemaining
                        )),
                        new LocalizedMessage(isINR ? "rec.goal.longterm.action.inr" : "rec.goal.longterm.action.default")
                ));
            }
        }

        // 6. Positive Monthly Surplus & Reserve Intact (Rule 1.d)
        if (!isBelowReserve && netSavingsRate.compareTo(BigDecimal.ZERO) > 0) {
            String actionable = isINR
                    ? "Direct surplus cash flow into structured long-term schemes like PPF or NPS for compounding and tax efficiency. Check current terms on the official site."
                    : "Direct surplus cash flow into disciplined long-term retirement accounts or diversified broad-market index funds.";
            String officialUrl = isINR ? schemeKnowledgeBase.getPPF().officialUrl() : null;

            recList.add(new FinancialRecommendationResponse.RecommendationItem(
                    "Surplus Allocation: Long-Term Savings",
                    "LOW",
                    String.format("With your emergency reserve intact and an estimated monthly surplus of %s, you have capacity for long-term disciplined wealth accumulation.",
                            formatMoney(netSavingsRate, currency)),
                    actionable,
                    "Savings",
                    officialUrl,
                    new LocalizedMessage("rec.surplus.title"),
                    new LocalizedMessage("rec.surplus.detail", Map.of("surplus", toMoney(netSavingsRate, currency))),
                    new LocalizedMessage(isINR ? "rec.surplus.action.inr" : "rec.surplus.action.default")
            ));
        }

        // 7. Investment Educational Content (Rule 5)
        recList.add(new FinancialRecommendationResponse.RecommendationItem(
                "Investment Options to Learn About",
                "LOW",
                "Asset classes grouped by risk profile: Low Risk (Bank Fixed/Recurring Deposits, Government Savings Bonds), Medium Risk (Balanced Hybrid Funds, Large-Cap Index Funds), High Risk (Broad Equity Index Funds, Sectoral Equities). General information, not investment advice.",
                "Build and preserve your emergency buffer before allocating capital to market-linked instruments. Never invest based on short-term price targets.",
                "Savings",
                null,
                new LocalizedMessage("rec.invest.edu.title"),
                new LocalizedMessage("rec.invest.edu.detail"),
                new LocalizedMessage("rec.invest.edu.action")
        ));

        // 8. Overall Status Level & Primary Recommendation
        String statusLevel = "SAFE";
        String primaryRec = "Your financial cash flow is healthy and all benchmarks are satisfied.";
        LocalizedMessage primaryMsg;

        if (lowestProjectedBalance.compareTo(BigDecimal.ZERO) < 0 || "HIGH".equals(riskLevel)) {
            statusLevel = "CRITICAL";
            primaryRec = "🚨 CRITICAL FINANCIAL NOTICE: Immediate spending reduction required to avoid negative balances and high risk escalation.";
            primaryMsg = new LocalizedMessage("rec.status.critical");
        } else if (isBelowReserve || "MEDIUM".equals(riskLevel) || recList.stream().anyMatch(r -> "HIGH".equals(r.priority()))) {
            statusLevel = "WARNING";
            primaryRec = "⚠️ FINANCIAL WARNING: Reserve threshold, gap, or moderate risk detected. Prioritize emergency reserves before major purchases.";
            primaryMsg = new LocalizedMessage("rec.status.warning");
        } else {
            statusLevel = "SAFE";
            primaryRec = "🟢 FINANCIAL HEALTH SAFE: Excellent cash flow stability, positive forward runway, and low risk score.";
            primaryMsg = new LocalizedMessage("rec.status.safe");
        }

        // 9. UI: At most top 5 recommendations by priority (HIGH > MEDIUM > LOW)
        List<FinancialRecommendationResponse.RecommendationItem> sortedTop5 = recList.stream()
                .sorted(Comparator.comparingInt((FinancialRecommendationResponse.RecommendationItem r) -> getPriorityRank(r.priority())).reversed())
                .limit(5)
                .collect(Collectors.toList());

        return new FinancialRecommendationResponse(
                statusLevel,
                primaryRec,
                reserveThreshold,
                isBelowReserve,
                sortedTop5,
                primaryMsg
        );
    }

    private static int getPriorityRank(String priority) {
        if ("HIGH".equalsIgnoreCase(priority)) return 3;
        if ("MEDIUM".equalsIgnoreCase(priority)) return 2;
        return 1; // LOW
    }

    private static boolean isMedicalOrEmergencyGoal(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.contains("medical") || lower.contains("health") || lower.contains("emergency")
                || lower.contains("hospital") || lower.contains("mediclaim");
    }

    private static boolean hasInsuranceTransactionInLast12Months(List<Transaction> transactions) {
        if (transactions == null || transactions.isEmpty()) return false;
        LocalDateTime cutoff = LocalDateTime.now().minusMonths(12);

        for (Transaction t : transactions) {
            if (t.getCreatedAt() != null && t.getCreatedAt().isAfter(cutoff)) {
                String cat = t.getCategory();
                String desc = t.getDescription() != null ? t.getDescription().toLowerCase() : "";
                if ("Insurance".equalsIgnoreCase(cat)) {
                    return true;
                }
                if (desc.contains("insurance") || desc.contains("premium") || desc.contains("lic")
                        || desc.contains("mediclaim") || desc.contains("health policy") || desc.contains("term plan")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Long getMonthsRemaining(GoalResponse goal) {
        if (goal == null) return null;
        if (goal.targetDate() != null) {
            long months = ChronoUnit.MONTHS.between(LocalDate.now(), goal.targetDate());
            return Math.max(1L, months);
        }
        if (goal.estimatedMonthsRemaining() > 0) {
            return (long) Math.ceil(goal.estimatedMonthsRemaining());
        }
        return null;
    }
}
