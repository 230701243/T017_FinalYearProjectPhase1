package com.financeapp.service;

import com.financeapp.dto.DecisionScenarioResult;
import com.financeapp.dto.LocalizedMessage;
import com.financeapp.dto.MoneyParam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class DecisionScenarioEvaluator {

    private static final BigDecimal DEFAULT_EMI_AFFORDABILITY_LIMIT = BigDecimal.valueOf(0.30);

    private static MoneyParam toMoney(BigDecimal usdAmount, String currencyCode) {
        return CurrencyConversionService.toMoney(usdAmount, currencyCode);
    }

    public record SavingsGoalContext(
            boolean hasGoal,
            String goalName,
            BigDecimal targetAmount,
            BigDecimal currentAmount,
            BigDecimal remainingAmount,
            boolean isAlreadyMet
    ) {
        public static SavingsGoalContext noGoal() {
            return new SavingsGoalContext(false, null, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, false);
        }

        public static SavingsGoalContext of(String name, BigDecimal target, BigDecimal current) {
            if (target == null || target.compareTo(BigDecimal.ZERO) <= 0) {
                return noGoal();
            }
            current = current != null ? current : BigDecimal.ZERO;
            BigDecimal remaining = target.subtract(current);
            boolean met = remaining.compareTo(BigDecimal.ZERO) <= 0;
            return new SavingsGoalContext(true, name, target, current, met ? BigDecimal.ZERO : remaining.setScale(2, RoundingMode.HALF_UP), met);
        }
    }

    private static String formatMoney(BigDecimal usdAmount, String currencyCode) {
        return CurrencyConversionService.formatMoney(usdAmount, currencyCode);
    }

    /**
     * Pure function to evaluate the Buy Now option without savings goal.
     */
    public static DecisionScenarioResult evaluateBuyNow(
            BigDecimal currentSavings,
            BigDecimal purchaseCost,
            BigDecimal reserveFloor
    ) {
        return evaluateBuyNow(currentSavings, purchaseCost, reserveFloor, SavingsGoalContext.noGoal(), BigDecimal.ZERO, "USD");
    }

    /**
     * Pure function to evaluate the Buy Now option with savings goal context and monthly savings pace.
     */
    public static DecisionScenarioResult evaluateBuyNow(
            BigDecimal currentSavings,
            BigDecimal purchaseCost,
            BigDecimal reserveFloor,
            SavingsGoalContext goalContext,
            BigDecimal avgMonthlyNetSavings
    ) {
        return evaluateBuyNow(currentSavings, purchaseCost, reserveFloor, goalContext, avgMonthlyNetSavings, "USD");
    }

    public static DecisionScenarioResult evaluateBuyNow(
            BigDecimal currentSavings,
            BigDecimal purchaseCost,
            BigDecimal reserveFloor,
            SavingsGoalContext goalContext,
            BigDecimal avgMonthlyNetSavings,
            String preferredCurrency
    ) {
        String currency = (preferredCurrency != null && !preferredCurrency.isBlank()) ? preferredCurrency : "USD";
        currentSavings = currentSavings != null ? currentSavings : BigDecimal.ZERO;
        purchaseCost = purchaseCost != null ? purchaseCost : BigDecimal.ZERO;
        reserveFloor = reserveFloor != null ? reserveFloor : BigDecimal.ZERO;
        goalContext = goalContext != null ? goalContext : SavingsGoalContext.noGoal();
        avgMonthlyNetSavings = avgMonthlyNetSavings != null ? avgMonthlyNetSavings : BigDecimal.ZERO;

        BigDecimal projectedSavings = currentSavings.subtract(purchaseCost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal bufferAboveFloor = projectedSavings.subtract(reserveFloor).setScale(2, RoundingMode.HALF_UP);

        List<String> warnings = new ArrayList<>();
        List<LocalizedMessage> warningMessages = new ArrayList<>();
        boolean feasible = true;

        if (projectedSavings.compareTo(BigDecimal.ZERO) < 0) {
            feasible = false;
            BigDecimal deficit = purchaseCost.subtract(currentSavings).setScale(2, RoundingMode.HALF_UP);
            warnings.add(String.format("Insufficient funds: Purchase cost (%s) exceeds current savings (%s) by %s.",
                    formatMoney(purchaseCost, currency), formatMoney(currentSavings, currency), formatMoney(deficit, currency)));
            warningMessages.add(new LocalizedMessage("warn.scenario.insufficientFunds", Map.of(
                    "cost", toMoney(purchaseCost, currency),
                    "savings", toMoney(currentSavings, currency),
                    "deficit", toMoney(deficit, currency)
            )));
        } else if (projectedSavings.compareTo(reserveFloor) < 0) {
            feasible = false;
            BigDecimal breachAmount = reserveFloor.subtract(projectedSavings).setScale(2, RoundingMode.HALF_UP);
            warnings.add(String.format("Reserve floor breached: Projected savings of %s falls below emergency reserve floor of %s by %s.",
                    formatMoney(projectedSavings, currency), formatMoney(reserveFloor, currency), formatMoney(breachAmount, currency)));
            warningMessages.add(new LocalizedMessage("warn.scenario.reserveBreached", Map.of(
                    "projected", toMoney(projectedSavings, currency),
                    "floor", toMoney(reserveFloor, currency),
                    "breach", toMoney(breachAmount, currency)
            )));
        }

        Map<String, Object> rawNumbers = new LinkedHashMap<>();
        rawNumbers.put("currentSavings", currentSavings);
        rawNumbers.put("purchaseCost", purchaseCost);
        rawNumbers.put("reserveFloor", reserveFloor);
        rawNumbers.put("projectedSavings", projectedSavings);
        rawNumbers.put("bufferAboveFloor", bufferAboveFloor);

        // Branch based on savings goal status
        if (!goalContext.hasGoal()) {
            rawNumbers.put("goalStatus", "NO_GOAL");
        } else if (goalContext.isAlreadyMet()) {
            rawNumbers.put("goalStatus", "ALREADY_MET");
            rawNumbers.put("goalName", goalContext.goalName());
            rawNumbers.put("goalTargetAmount", goalContext.targetAmount());
            rawNumbers.put("goalCurrentAmount", goalContext.currentAmount());
            rawNumbers.put("goalRemainingAmount", BigDecimal.ZERO);
            rawNumbers.put("goalImpactSummary", String.format("Savings goal '%s' is already met/exceeded (Target: %s, Saved: %s). Purchase evaluated on standard cash-flow/reserve logic without threatening the goal.",
                    goalContext.goalName(), formatMoney(goalContext.targetAmount(), currency), formatMoney(goalContext.currentAmount(), currency)));
        } else {
            rawNumbers.put("goalStatus", "NOT_MET");
            rawNumbers.put("goalName", goalContext.goalName());
            rawNumbers.put("goalTargetAmount", goalContext.targetAmount());
            rawNumbers.put("goalCurrentAmount", goalContext.currentAmount());
            rawNumbers.put("goalRemainingAmount", goalContext.remainingAmount());

            if (avgMonthlyNetSavings.compareTo(BigDecimal.ZERO) > 0) {
                double baselineMonths = goalContext.remainingAmount().divide(avgMonthlyNetSavings, 2, RoundingMode.HALF_UP).doubleValue();
                double delayMonths = purchaseCost.divide(avgMonthlyNetSavings, 2, RoundingMode.HALF_UP).doubleValue();
                double projectedMonths = baselineMonths + delayMonths;
                long monthsToAdd = (long) Math.ceil(projectedMonths);
                YearMonth completionYM = YearMonth.now().plusMonths(monthsToAdd);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
                String projectedDate = completionYM.format(formatter);

                rawNumbers.put("goalBaselineMonthsToComplete", baselineMonths);
                rawNumbers.put("goalProjectedMonthsToComplete", projectedMonths);
                rawNumbers.put("goalDelayMonths", delayMonths);
                rawNumbers.put("goalProjectedCompletionDate", projectedDate);
                rawNumbers.put("goalImpactSummary", String.format("Immediate purchase delays goal '%s' (%s remaining) by ~%.1f months, shifting completion to %s.",
                        goalContext.goalName(), formatMoney(goalContext.remainingAmount(), currency), delayMonths, projectedDate));
                if (delayMonths > 0) {
                    warnings.add(String.format("Goal delay: Upfront purchase of %s delays goal '%s' by ~%.1f months.",
                            formatMoney(purchaseCost, currency), goalContext.goalName(), delayMonths));
                    warningMessages.add(new LocalizedMessage("warn.scenario.goalDelay", Map.of(
                            "cost", toMoney(purchaseCost, currency),
                            "goalName", goalContext.goalName(),
                            "delay", Math.round(delayMonths * 10.0) / 10.0
                    )));
                }
            } else {
                rawNumbers.put("goalProjectedCompletionDate", "Indefinite (Monthly savings <= " + formatMoney(BigDecimal.ZERO, currency) + ")");
                rawNumbers.put("goalImpactSummary", String.format("Goal '%s' completion is indefinite due to non-positive monthly savings.", goalContext.goalName()));
            }
        }

        return new DecisionScenarioResult(feasible, projectedSavings, warnings, rawNumbers, warningMessages);
    }

    /**
     * Pure function to evaluate the Wait Option without goal context.
     */
    public static DecisionScenarioResult evaluateWaitOption(
            BigDecimal currentSavings,
            BigDecimal purchaseCost,
            BigDecimal reserveFloor,
            List<BigDecimal> monthlyNetSavingsHistoryLast6Months
    ) {
        return evaluateWaitOption(currentSavings, purchaseCost, reserveFloor, monthlyNetSavingsHistoryLast6Months, SavingsGoalContext.noGoal(), "USD");
    }

    /**
     * Pure function to evaluate the Wait Option with dynamic N months wait and goal context.
     */
    public static DecisionScenarioResult evaluateWaitOption(
            BigDecimal currentSavings,
            BigDecimal purchaseCost,
            BigDecimal reserveFloor,
            List<BigDecimal> monthlyNetSavingsHistoryLast6Months,
            SavingsGoalContext goalContext
    ) {
        return evaluateWaitOption(currentSavings, purchaseCost, reserveFloor, monthlyNetSavingsHistoryLast6Months, goalContext, "USD");
    }

    public static DecisionScenarioResult evaluateWaitOption(
            BigDecimal currentSavings,
            BigDecimal purchaseCost,
            BigDecimal reserveFloor,
            List<BigDecimal> monthlyNetSavingsHistoryLast6Months,
            SavingsGoalContext goalContext,
            String preferredCurrency
    ) {
        String currency = (preferredCurrency != null && !preferredCurrency.isBlank()) ? preferredCurrency : "USD";
        currentSavings = currentSavings != null ? currentSavings : BigDecimal.ZERO;
        purchaseCost = purchaseCost != null ? purchaseCost : BigDecimal.ZERO;
        reserveFloor = reserveFloor != null ? reserveFloor : BigDecimal.ZERO;
        goalContext = goalContext != null ? goalContext : SavingsGoalContext.noGoal();

        List<BigDecimal> history = monthlyNetSavingsHistoryLast6Months != null ? monthlyNetSavingsHistoryLast6Months : Collections.emptyList();

        BigDecimal sumNetSavings = BigDecimal.ZERO;
        for (BigDecimal val : history) {
            if (val != null) {
                sumNetSavings = sumNetSavings.add(val);
            }
        }

        int count = history.isEmpty() ? 1 : history.size();
        BigDecimal avgMonthlyNetSavings = sumNetSavings.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

        BigDecimal targetRequired = purchaseCost.add(reserveFloor);
        BigDecimal targetDeficit = targetRequired.subtract(currentSavings);
        if (targetDeficit.compareTo(BigDecimal.ZERO) < 0) {
            targetDeficit = BigDecimal.ZERO;
        }

        List<String> warnings = new ArrayList<>();
        List<LocalizedMessage> warningMessages = new ArrayList<>();
        boolean feasible;
        int monthsToWait = 0;
        BigDecimal projectedSavingsAfterPurchase;
        BigDecimal projectedSavingsBeforePurchase;

        if (targetDeficit.compareTo(BigDecimal.ZERO) == 0) {
            feasible = true;
            monthsToWait = 0;
            projectedSavingsBeforePurchase = currentSavings;
            projectedSavingsAfterPurchase = currentSavings.subtract(purchaseCost).setScale(2, RoundingMode.HALF_UP);
        } else if (avgMonthlyNetSavings.compareTo(BigDecimal.ZERO) <= 0) {
            feasible = false;
            monthsToWait = -1;
            projectedSavingsBeforePurchase = currentSavings;
            projectedSavingsAfterPurchase = currentSavings.subtract(purchaseCost).setScale(2, RoundingMode.HALF_UP);
            warnings.add(String.format("Average actual monthly net savings is non-positive (%s/mo). Waiting will not accumulate required funds without expense reduction.",
                    formatMoney(avgMonthlyNetSavings, currency)));
            warningMessages.add(new LocalizedMessage("warn.scenario.nonPositiveSavingsWait", Map.of(
                    "avgNet", toMoney(avgMonthlyNetSavings, currency)
            )));
        } else {
            double monthsDouble = targetDeficit.divide(avgMonthlyNetSavings, 4, RoundingMode.HALF_UP).doubleValue();
            monthsToWait = (int) Math.ceil(monthsDouble);
            projectedSavingsBeforePurchase = currentSavings.add(avgMonthlyNetSavings.multiply(BigDecimal.valueOf(monthsToWait))).setScale(2, RoundingMode.HALF_UP);
            projectedSavingsAfterPurchase = projectedSavingsBeforePurchase.subtract(purchaseCost).setScale(2, RoundingMode.HALF_UP);

            if (monthsToWait > 12) {
                feasible = false;
                warnings.add(String.format("Estimated wait time of %d months exceeds the 12-month short-term horizon.", monthsToWait));
                warningMessages.add(new LocalizedMessage("warn.scenario.waitExceedsHorizon", Map.of("months", monthsToWait)));
            } else {
                feasible = true;
            }
        }

        Map<String, Object> rawNumbers = new LinkedHashMap<>();
        rawNumbers.put("currentSavings", currentSavings);
        rawNumbers.put("purchaseCost", purchaseCost);
        rawNumbers.put("reserveFloor", reserveFloor);
        rawNumbers.put("targetRequired", targetRequired);
        rawNumbers.put("targetDeficit", targetDeficit);
        rawNumbers.put("avgMonthlyNetSavings", avgMonthlyNetSavings);
        rawNumbers.put("monthsToWait", monthsToWait);
        rawNumbers.put("projectedSavingsBeforePurchase", projectedSavingsBeforePurchase);
        rawNumbers.put("projectedSavingsAfterPurchase", projectedSavingsAfterPurchase);
        rawNumbers.put("historyMonthsCount", history.size());

        // Branch based on savings goal status
        if (!goalContext.hasGoal()) {
            rawNumbers.put("goalStatus", "NO_GOAL");
        } else if (goalContext.isAlreadyMet()) {
            rawNumbers.put("goalStatus", "ALREADY_MET");
            rawNumbers.put("goalName", goalContext.goalName());
            rawNumbers.put("goalTargetAmount", goalContext.targetAmount());
            rawNumbers.put("goalCurrentAmount", goalContext.currentAmount());
            rawNumbers.put("goalRemainingAmount", BigDecimal.ZERO);
            rawNumbers.put("goalImpactSummary", String.format("Savings goal '%s' is already met/exceeded. Purchase evaluated on standard cash-flow/reserve logic without threatening the goal.",
                    goalContext.goalName()));
        } else {
            rawNumbers.put("goalStatus", "NOT_MET");
            rawNumbers.put("goalName", goalContext.goalName());
            rawNumbers.put("goalTargetAmount", goalContext.targetAmount());
            rawNumbers.put("goalCurrentAmount", goalContext.currentAmount());
            rawNumbers.put("goalRemainingAmount", goalContext.remainingAmount());

            if (avgMonthlyNetSavings.compareTo(BigDecimal.ZERO) > 0) {
                double baselineMonths = goalContext.remainingAmount().divide(avgMonthlyNetSavings, 2, RoundingMode.HALF_UP).doubleValue();
                double delayMonths = Math.max(0, monthsToWait);
                double projectedMonths = baselineMonths + delayMonths;
                long monthsToAdd = (long) Math.ceil(projectedMonths);
                YearMonth completionYM = YearMonth.now().plusMonths(monthsToAdd);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
                String projectedDate = completionYM.format(formatter);

                rawNumbers.put("goalBaselineMonthsToComplete", baselineMonths);
                rawNumbers.put("goalProjectedMonthsToComplete", projectedMonths);
                rawNumbers.put("goalDelayMonths", delayMonths);
                rawNumbers.put("goalProjectedCompletionDate", projectedDate);
                rawNumbers.put("goalImpactSummary", String.format("Waiting %d months to accumulate purchase funds defers goal '%s' completion to ~%s (~%.1f mos delay).",
                        monthsToWait, goalContext.goalName(), projectedDate, delayMonths));
            } else {
                rawNumbers.put("goalProjectedCompletionDate", "Indefinite (Monthly savings <= " + formatMoney(BigDecimal.ZERO, currency) + ")");
                rawNumbers.put("goalImpactSummary", String.format("Goal '%s' completion is indefinite due to non-positive monthly savings.", goalContext.goalName()));
            }
        }

        return new DecisionScenarioResult(feasible, projectedSavingsAfterPurchase, warnings, rawNumbers, warningMessages);
    }

    /**
     * Pure function to evaluate a user-specified custom target month without goal context.
     */
    public static DecisionScenarioResult evaluateCustomTimingOption(
            BigDecimal currentSavings,
            BigDecimal purchaseCost,
            BigDecimal reserveFloor,
            List<BigDecimal> monthlyNetSavingsHistoryLast6Months,
            int targetMonthsInFuture
    ) {
        return evaluateCustomTimingOption(currentSavings, purchaseCost, reserveFloor, monthlyNetSavingsHistoryLast6Months, targetMonthsInFuture, SavingsGoalContext.noGoal(), "USD");
    }

    /**
     * Pure function to evaluate a user-specified custom target month with goal context.
     */
    public static DecisionScenarioResult evaluateCustomTimingOption(
            BigDecimal currentSavings,
            BigDecimal purchaseCost,
            BigDecimal reserveFloor,
            List<BigDecimal> monthlyNetSavingsHistoryLast6Months,
            int targetMonthsInFuture,
            SavingsGoalContext goalContext
    ) {
        return evaluateCustomTimingOption(currentSavings, purchaseCost, reserveFloor, monthlyNetSavingsHistoryLast6Months, targetMonthsInFuture, goalContext, "USD");
    }

    public static DecisionScenarioResult evaluateCustomTimingOption(
            BigDecimal currentSavings,
            BigDecimal purchaseCost,
            BigDecimal reserveFloor,
            List<BigDecimal> monthlyNetSavingsHistoryLast6Months,
            int targetMonthsInFuture,
            SavingsGoalContext goalContext,
            String preferredCurrency
    ) {
        String currency = (preferredCurrency != null && !preferredCurrency.isBlank()) ? preferredCurrency : "USD";
        currentSavings = currentSavings != null ? currentSavings : BigDecimal.ZERO;
        purchaseCost = purchaseCost != null ? purchaseCost : BigDecimal.ZERO;
        reserveFloor = reserveFloor != null ? reserveFloor : BigDecimal.ZERO;
        goalContext = goalContext != null ? goalContext : SavingsGoalContext.noGoal();

        List<BigDecimal> history = monthlyNetSavingsHistoryLast6Months != null ? monthlyNetSavingsHistoryLast6Months : Collections.emptyList();

        BigDecimal sumNetSavings = BigDecimal.ZERO;
        for (BigDecimal val : history) {
            if (val != null) {
                sumNetSavings = sumNetSavings.add(val);
            }
        }
        int count = history.isEmpty() ? 1 : history.size();
        BigDecimal avgMonthlyNetSavings = sumNetSavings.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);

        List<String> warnings = new ArrayList<>();
        List<LocalizedMessage> warningMessages = new ArrayList<>();
        boolean feasible = true;

        int months = targetMonthsInFuture;
        if (months < 0) {
            months = 0;
            warnings.add("Target month is in the past; evaluated as immediate purchase (month 0).");
            warningMessages.add(new LocalizedMessage("warn.scenario.timingPast"));
        } else if (months > 24) {
            months = 24;
            warnings.add("Target month exceeds the 24-month horizon; evaluated at month 24.");
            warningMessages.add(new LocalizedMessage("warn.scenario.timingExceeds24"));
        }

        BigDecimal accumulatedSavings = avgMonthlyNetSavings.multiply(BigDecimal.valueOf(months)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal projectedSavingsBeforePurchase = currentSavings.add(accumulatedSavings).setScale(2, RoundingMode.HALF_UP);
        BigDecimal projectedSavingsAfterPurchase = projectedSavingsBeforePurchase.subtract(purchaseCost).setScale(2, RoundingMode.HALF_UP);
        BigDecimal bufferAboveFloor = projectedSavingsAfterPurchase.subtract(reserveFloor).setScale(2, RoundingMode.HALF_UP);

        YearMonth targetYM = YearMonth.now().plusMonths(months);
        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
        String targetMonthFormatted = targetYM.format(monthFormatter);

        if (projectedSavingsAfterPurchase.compareTo(BigDecimal.ZERO) < 0) {
            feasible = false;
            BigDecimal deficit = purchaseCost.subtract(projectedSavingsBeforePurchase).setScale(2, RoundingMode.HALF_UP);
            warnings.add(String.format("Insufficient funds at %s (in %d mos): Projected savings (%s) falls short of purchase cost (%s) by %s.",
                    targetMonthFormatted, months, formatMoney(projectedSavingsBeforePurchase, currency), formatMoney(purchaseCost, currency), formatMoney(deficit, currency)));
            warningMessages.add(new LocalizedMessage("warn.scenario.insufficientFundsTiming", Map.of(
                    "date", targetMonthFormatted,
                    "months", months,
                    "projected", toMoney(projectedSavingsBeforePurchase, currency),
                    "cost", toMoney(purchaseCost, currency),
                    "deficit", toMoney(deficit, currency)
            )));
        } else if (projectedSavingsAfterPurchase.compareTo(reserveFloor) < 0) {
            feasible = false;
            BigDecimal breachAmount = reserveFloor.subtract(projectedSavingsAfterPurchase).setScale(2, RoundingMode.HALF_UP);
            warnings.add(String.format("Reserve floor breached at %s (in %d mos): Projected savings after purchase (%s) falls below emergency reserve floor of %s by %s.",
                    targetMonthFormatted, months, formatMoney(projectedSavingsAfterPurchase, currency), formatMoney(reserveFloor, currency), formatMoney(breachAmount, currency)));
            warningMessages.add(new LocalizedMessage("warn.scenario.reserveBreachedTiming", Map.of(
                    "date", targetMonthFormatted,
                    "months", months,
                    "projected", toMoney(projectedSavingsAfterPurchase, currency),
                    "floor", toMoney(reserveFloor, currency),
                    "breach", toMoney(breachAmount, currency)
            )));
        }

        Map<String, Object> rawNumbers = new LinkedHashMap<>();
        rawNumbers.put("currentSavings", currentSavings);
        rawNumbers.put("purchaseCost", purchaseCost);
        rawNumbers.put("reserveFloor", reserveFloor);
        rawNumbers.put("targetMonthsInFuture", months);
        rawNumbers.put("targetMonthFormatted", targetMonthFormatted);
        rawNumbers.put("avgMonthlyNetSavings", avgMonthlyNetSavings);
        rawNumbers.put("accumulatedSavings", accumulatedSavings);
        rawNumbers.put("projectedSavingsBeforePurchase", projectedSavingsBeforePurchase);
        rawNumbers.put("projectedSavingsAfterPurchase", projectedSavingsAfterPurchase);
        rawNumbers.put("bufferAboveFloor", bufferAboveFloor);

        // Branch based on savings goal status
        if (!goalContext.hasGoal()) {
            rawNumbers.put("goalStatus", "NO_GOAL");
        } else if (goalContext.isAlreadyMet()) {
            rawNumbers.put("goalStatus", "ALREADY_MET");
            rawNumbers.put("goalName", goalContext.goalName());
            rawNumbers.put("goalTargetAmount", goalContext.targetAmount());
            rawNumbers.put("goalCurrentAmount", goalContext.currentAmount());
            rawNumbers.put("goalRemainingAmount", BigDecimal.ZERO);
            rawNumbers.put("goalImpactSummary", String.format("Savings goal '%s' is already met/exceeded. Custom timing evaluated without threatening the goal.",
                    goalContext.goalName()));
        } else {
            rawNumbers.put("goalStatus", "NOT_MET");
            rawNumbers.put("goalName", goalContext.goalName());
            rawNumbers.put("goalTargetAmount", goalContext.targetAmount());
            rawNumbers.put("goalCurrentAmount", goalContext.currentAmount());
            rawNumbers.put("goalRemainingAmount", goalContext.remainingAmount());

            if (avgMonthlyNetSavings.compareTo(BigDecimal.ZERO) > 0) {
                double baselineMonths = goalContext.remainingAmount().divide(avgMonthlyNetSavings, 2, RoundingMode.HALF_UP).doubleValue();
                double delayMonths = months;
                double projectedMonths = baselineMonths + delayMonths;
                long monthsToAdd = (long) Math.ceil(projectedMonths);
                YearMonth completionYM = YearMonth.now().plusMonths(monthsToAdd);
                String projectedDate = completionYM.format(monthFormatter);

                rawNumbers.put("goalBaselineMonthsToComplete", baselineMonths);
                rawNumbers.put("goalProjectedMonthsToComplete", projectedMonths);
                rawNumbers.put("goalDelayMonths", delayMonths);
                rawNumbers.put("goalProjectedCompletionDate", projectedDate);
                rawNumbers.put("goalImpactSummary", String.format("Purchasing in %s (%d mos) shifts goal '%s' completion to %s (~%.1f mos delay).",
                        targetMonthFormatted, months, goalContext.goalName(), projectedDate, delayMonths));
            } else {
                rawNumbers.put("goalProjectedCompletionDate", "Indefinite (Monthly savings <= " + formatMoney(BigDecimal.ZERO, currency) + ")");
                rawNumbers.put("goalImpactSummary", String.format("Goal '%s' completion is indefinite due to non-positive monthly savings.", goalContext.goalName()));
            }
        }

        return new DecisionScenarioResult(feasible, projectedSavingsAfterPurchase, warnings, rawNumbers, warningMessages);
    }

    /**
     * Pure function to evaluate EMI Option with default affordability limit of 0.30 and without goal context.
     */
    public static DecisionScenarioResult evaluateEmiOption(
            BigDecimal purchaseCost,
            BigDecimal emiAmount,
            BigDecimal salary,
            BigDecimal fixedExpenses,
            BigDecimal minSavingsGoal
    ) {
        return evaluateEmiOption(purchaseCost, emiAmount, salary, fixedExpenses, minSavingsGoal, DEFAULT_EMI_AFFORDABILITY_LIMIT, SavingsGoalContext.noGoal(), 6, "USD");
    }

    /**
     * Pure function to evaluate EMI Option with goal context and tenure.
     */
    public static DecisionScenarioResult evaluateEmiOption(
            BigDecimal purchaseCost,
            BigDecimal emiAmount,
            BigDecimal salary,
            BigDecimal fixedExpenses,
            BigDecimal minSavingsGoal,
            BigDecimal emiAffordabilityRatioLimit,
            SavingsGoalContext goalContext,
            int tenureMonths
    ) {
        return evaluateEmiOption(purchaseCost, emiAmount, salary, fixedExpenses, minSavingsGoal, emiAffordabilityRatioLimit, goalContext, tenureMonths, "USD");
    }

    public static DecisionScenarioResult evaluateEmiOption(
            BigDecimal purchaseCost,
            BigDecimal emiAmount,
            BigDecimal salary,
            BigDecimal fixedExpenses,
            BigDecimal minSavingsGoal,
            BigDecimal emiAffordabilityRatioLimit,
            SavingsGoalContext goalContext,
            int tenureMonths,
            String preferredCurrency
    ) {
        String currency = (preferredCurrency != null && !preferredCurrency.isBlank()) ? preferredCurrency : "USD";
        purchaseCost = purchaseCost != null ? purchaseCost : BigDecimal.ZERO;
        emiAmount = emiAmount != null ? emiAmount : BigDecimal.ZERO;
        salary = salary != null ? salary : BigDecimal.ZERO;
        fixedExpenses = fixedExpenses != null ? fixedExpenses : BigDecimal.ZERO;
        minSavingsGoal = minSavingsGoal != null ? minSavingsGoal : BigDecimal.ZERO;
        emiAffordabilityRatioLimit = emiAffordabilityRatioLimit != null ? emiAffordabilityRatioLimit : DEFAULT_EMI_AFFORDABILITY_LIMIT;
        goalContext = goalContext != null ? goalContext : SavingsGoalContext.noGoal();

        List<String> warnings = new ArrayList<>();
        List<LocalizedMessage> warningMessages = new ArrayList<>();
        boolean feasible = true;

        // Validate tenure: positive integer in reasonable range (1-60)
        int tenure = tenureMonths;
        if (tenure < 1) {
            tenure = 1;
            warnings.add("EMI tenure adjusted: Minimum allowed tenure is 1 month.");
            warningMessages.add(new LocalizedMessage("warn.scenario.emiTenureMin"));
        } else if (tenure > 60) {
            tenure = 60;
            warnings.add("EMI tenure adjusted: Maximum allowed tenure is 60 months.");
            warningMessages.add(new LocalizedMessage("warn.scenario.emiTenureMax"));
        }

        // Manual EMI amount is independent of tenure/cost; fallback to cost/tenure if not supplied
        BigDecimal finalEmiAmount = emiAmount != null && emiAmount.compareTo(BigDecimal.ZERO) > 0
                ? emiAmount
                : (purchaseCost.compareTo(BigDecimal.ZERO) > 0 ? purchaseCost.divide(BigDecimal.valueOf(tenure), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);

        BigDecimal disposableIncome = salary.subtract(fixedExpenses).setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingBudget = salary.subtract(fixedExpenses).subtract(minSavingsGoal).subtract(finalEmiAmount).setScale(2, RoundingMode.HALF_UP);

        BigDecimal emiToSalaryRatio = BigDecimal.ZERO;
        if (salary.compareTo(BigDecimal.ZERO) > 0) {
            emiToSalaryRatio = finalEmiAmount.divide(salary, 4, RoundingMode.HALF_UP);
        } else if (finalEmiAmount.compareTo(BigDecimal.ZERO) > 0) {
            emiToSalaryRatio = BigDecimal.ONE;
        }

        if (emiToSalaryRatio.compareTo(emiAffordabilityRatioLimit) > 0) {
            feasible = false;
            double actualPct = emiToSalaryRatio.multiply(BigDecimal.valueOf(100)).doubleValue();
            double limitPct = emiAffordabilityRatioLimit.multiply(BigDecimal.valueOf(100)).doubleValue();
            warnings.add(String.format("EMI affordability breached: Monthly EMI of %s is %.1f%% of salary, exceeding the maximum limit of %.1f%%.",
                    formatMoney(finalEmiAmount, currency), actualPct, limitPct));
            warningMessages.add(new LocalizedMessage("warn.scenario.emiAffordability", Map.of(
                    "emi", toMoney(finalEmiAmount, currency),
                    "actualPct", Math.round(actualPct * 10.0) / 10.0,
                    "limitPct", Math.round(limitPct * 10.0) / 10.0
            )));
        }

        if (remainingBudget.compareTo(BigDecimal.ZERO) < 0) {
            feasible = false;
            warnings.add(String.format("Budget deficit: Monthly uncommitted budget is negative (%s) after EMI (%s), fixed expenses (%s), and savings goal (%s).",
                    formatMoney(remainingBudget, currency), formatMoney(finalEmiAmount, currency), formatMoney(fixedExpenses, currency), formatMoney(minSavingsGoal, currency)));
            warningMessages.add(new LocalizedMessage("warn.scenario.budgetDeficit", Map.of(
                    "budget", toMoney(remainingBudget, currency),
                    "emi", toMoney(finalEmiAmount, currency),
                    "fixed", toMoney(fixedExpenses, currency),
                    "goal", toMoney(minSavingsGoal, currency)
            )));
        }

        if (disposableIncome.compareTo(finalEmiAmount) < 0) {
            feasible = false;
            warnings.add(String.format("Disposable income deficit: EMI (%s) exceeds total disposable income (%s).",
                    formatMoney(finalEmiAmount, currency), formatMoney(disposableIncome, currency)));
            warningMessages.add(new LocalizedMessage("warn.scenario.disposableDeficit", Map.of(
                    "emi", toMoney(finalEmiAmount, currency),
                    "disposable", toMoney(disposableIncome, currency)
            )));
        }

        Map<String, Object> rawNumbers = new LinkedHashMap<>();
        rawNumbers.put("purchaseCost", purchaseCost);
        rawNumbers.put("emiAmount", finalEmiAmount);
        rawNumbers.put("salary", salary);
        rawNumbers.put("fixedExpenses", fixedExpenses);
        rawNumbers.put("minSavingsGoal", minSavingsGoal);
        rawNumbers.put("disposableIncome", disposableIncome);
        rawNumbers.put("remainingBudget", remainingBudget);
        rawNumbers.put("emiToSalaryRatio", emiToSalaryRatio);
        rawNumbers.put("emiAffordabilityRatioLimit", emiAffordabilityRatioLimit);
        rawNumbers.put("tenureMonths", tenure);

        // Branch based on savings goal status
        if (!goalContext.hasGoal()) {
            rawNumbers.put("goalStatus", "NO_GOAL");
        } else if (goalContext.isAlreadyMet()) {
            rawNumbers.put("goalStatus", "ALREADY_MET");
            rawNumbers.put("goalName", goalContext.goalName());
            rawNumbers.put("goalTargetAmount", goalContext.targetAmount());
            rawNumbers.put("goalCurrentAmount", goalContext.currentAmount());
            rawNumbers.put("goalRemainingAmount", BigDecimal.ZERO);
            rawNumbers.put("goalImpactSummary", String.format("Savings goal '%s' is already met/exceeded. EMI evaluated on standard affordability limits without threatening the goal.",
                    goalContext.goalName()));
        } else {
            rawNumbers.put("goalStatus", "NOT_MET");
            rawNumbers.put("goalName", goalContext.goalName());
            rawNumbers.put("goalTargetAmount", goalContext.targetAmount());
            rawNumbers.put("goalCurrentAmount", goalContext.currentAmount());
            rawNumbers.put("goalRemainingAmount", goalContext.remainingAmount());

            BigDecimal monthlyNetSavingsBeforeEmi = salary.subtract(fixedExpenses);
            BigDecimal monthlyNetSavingsDuringEmi = monthlyNetSavingsBeforeEmi.subtract(finalEmiAmount);

            if (monthlyNetSavingsBeforeEmi.compareTo(BigDecimal.ZERO) > 0) {
                double baselineMonths = goalContext.remainingAmount().divide(monthlyNetSavingsBeforeEmi, 2, RoundingMode.HALF_UP).doubleValue();
                double projectedMonths;

                if (monthlyNetSavingsDuringEmi.compareTo(BigDecimal.ZERO) <= 0) {
                    projectedMonths = tenure + baselineMonths;
                    warnings.add(String.format("Goal paused: EMI of %s/mo absorbs available savings, halting progress towards goal '%s' for %d months.",
                            formatMoney(finalEmiAmount, currency), goalContext.goalName(), tenure));
                    warningMessages.add(new LocalizedMessage("warn.scenario.goalPaused", Map.of(
                            "emi", toMoney(finalEmiAmount, currency),
                            "goalName", goalContext.goalName(),
                            "tenure", tenure
                    )));
                } else {
                    BigDecimal savingsDuringTenure = monthlyNetSavingsDuringEmi.multiply(BigDecimal.valueOf(tenure));
                    if (savingsDuringTenure.compareTo(goalContext.remainingAmount()) >= 0) {
                        projectedMonths = goalContext.remainingAmount().divide(monthlyNetSavingsDuringEmi, 2, RoundingMode.HALF_UP).doubleValue();
                    } else {
                        BigDecimal remainingAfterTenure = goalContext.remainingAmount().subtract(savingsDuringTenure);
                        double monthsAfterTenure = remainingAfterTenure.divide(monthlyNetSavingsBeforeEmi, 2, RoundingMode.HALF_UP).doubleValue();
                        projectedMonths = tenure + monthsAfterTenure;
                    }
                }

                double delayMonths = Math.max(0, projectedMonths - baselineMonths);
                long monthsToAdd = (long) Math.ceil(projectedMonths);
                YearMonth completionYM = YearMonth.now().plusMonths(monthsToAdd);
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
                String projectedDate = completionYM.format(formatter);

                rawNumbers.put("goalBaselineMonthsToComplete", baselineMonths);
                rawNumbers.put("goalProjectedMonthsToComplete", projectedMonths);
                rawNumbers.put("goalDelayMonths", delayMonths);
                rawNumbers.put("goalProjectedCompletionDate", projectedDate);
                rawNumbers.put("goalImpactSummary", String.format("EMI of %s/mo reduces monthly goal savings, completing goal '%s' in ~%s (~%.1f mos delay).",
                        formatMoney(finalEmiAmount, currency), goalContext.goalName(), projectedDate, delayMonths));
            } else {
                rawNumbers.put("goalProjectedCompletionDate", "Indefinite (Monthly savings <= " + formatMoney(BigDecimal.ZERO, currency) + ")");
                rawNumbers.put("goalImpactSummary", String.format("Goal '%s' completion is indefinite due to non-positive monthly savings.", goalContext.goalName()));
            }
        }

        return new DecisionScenarioResult(feasible, remainingBudget, warnings, rawNumbers, warningMessages);
    }
}
