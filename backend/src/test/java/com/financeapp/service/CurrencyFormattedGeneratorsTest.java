package com.financeapp.service;

import com.financeapp.dto.DecisionScenarioResult;
import com.financeapp.dto.GoalResponse;
import com.financeapp.dto.ThreeMonthCashFlowResponse;
import com.financeapp.dto.WhatIfResponse;
import com.financeapp.model.SavingsGoal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CurrencyFormattedGeneratorsTest {

    @Test
    @DisplayName("DecisionScenarioEvaluator Buy Now - INR produces ₹ and no $, USD produces $")
    void testBuyNowScenarioWarnings_Currency() {
        BigDecimal savings = BigDecimal.valueOf(300);
        BigDecimal cost = BigDecimal.valueOf(500);
        BigDecimal reserve = BigDecimal.valueOf(100);

        // INR: Deficit is $200 * 83.5 = ₹16,700.00
        DecisionScenarioResult inrResult = DecisionScenarioEvaluator.evaluateBuyNow(
                savings, cost, reserve, DecisionScenarioEvaluator.SavingsGoalContext.noGoal(), BigDecimal.ZERO, "INR"
        );
        assertFalse(inrResult.feasible());
        assertFalse(inrResult.warnings().isEmpty());
        for (String w : inrResult.warnings()) {
            assertFalse(w.contains("$"), "Warning must not contain $ for INR: " + w);
            assertTrue(w.contains("₹"), "Warning must contain ₹ for INR: " + w);
        }
        assertTrue(inrResult.warnings().get(0).contains("₹16,700.00"));

        // USD: Deficit is $200.00
        DecisionScenarioResult usdResult = DecisionScenarioEvaluator.evaluateBuyNow(
                savings, cost, reserve, DecisionScenarioEvaluator.SavingsGoalContext.noGoal(), BigDecimal.ZERO, "USD"
        );
        assertFalse(usdResult.feasible());
        for (String w : usdResult.warnings()) {
            assertTrue(w.contains("$"), "Warning must contain $ for USD: " + w);
            assertFalse(w.contains("₹"), "Warning must not contain ₹ for USD: " + w);
        }
        assertTrue(usdResult.warnings().get(0).contains("$200.00"));
    }

    @Test
    @DisplayName("DecisionScenarioEvaluator Wait Option - INR produces ₹ and no $, USD produces $")
    void testWaitOptionScenarioWarnings_Currency() {
        BigDecimal savings = BigDecimal.valueOf(100);
        BigDecimal cost = BigDecimal.valueOf(500);
        BigDecimal reserve = BigDecimal.valueOf(100);
        List<BigDecimal> negativeHistory = List.of(BigDecimal.valueOf(-50)); // Non-positive savings pace

        // INR
        DecisionScenarioResult inrResult = DecisionScenarioEvaluator.evaluateWaitOption(
                savings, cost, reserve, negativeHistory, DecisionScenarioEvaluator.SavingsGoalContext.noGoal(), "INR"
        );
        assertFalse(inrResult.feasible());
        for (String w : inrResult.warnings()) {
            assertFalse(w.contains("$"));
            assertTrue(w.contains("₹"));
        }
        // -50 * 83.5 = -4,175.00
        assertTrue(inrResult.warnings().get(0).contains("₹-4,175.00/mo"));

        // USD
        DecisionScenarioResult usdResult = DecisionScenarioEvaluator.evaluateWaitOption(
                savings, cost, reserve, negativeHistory, DecisionScenarioEvaluator.SavingsGoalContext.noGoal(), "USD"
        );
        assertFalse(usdResult.feasible());
        for (String w : usdResult.warnings()) {
            assertTrue(w.contains("$"));
            assertFalse(w.contains("₹"));
        }
        assertTrue(usdResult.warnings().get(0).contains("$-50.00/mo"));
    }

    @Test
    @DisplayName("DecisionScenarioEvaluator EMI Option - INR produces ₹ and no $, USD produces $")
    void testEmiOptionScenarioWarnings_Currency() {
        BigDecimal cost = BigDecimal.valueOf(2000);
        BigDecimal emi = BigDecimal.valueOf(500);
        BigDecimal salary = BigDecimal.valueOf(1000); // 500/1000 = 50% > 30% limit
        BigDecimal fixed = BigDecimal.valueOf(300);
        BigDecimal minSavings = BigDecimal.valueOf(200);

        // INR
        DecisionScenarioResult inrResult = DecisionScenarioEvaluator.evaluateEmiOption(
                cost, emi, salary, fixed, minSavings, BigDecimal.valueOf(0.30),
                DecisionScenarioEvaluator.SavingsGoalContext.noGoal(), 6, "INR"
        );
        assertFalse(inrResult.feasible());
        for (String w : inrResult.warnings()) {
            assertFalse(w.contains("$"));
            assertTrue(w.contains("₹"));
        }
        // 500 * 83.5 = 41,750.00
        assertTrue(inrResult.warnings().stream().anyMatch(w -> w.contains("₹41,750.00")));

        // USD
        DecisionScenarioResult usdResult = DecisionScenarioEvaluator.evaluateEmiOption(
                cost, emi, salary, fixed, minSavings, BigDecimal.valueOf(0.30),
                DecisionScenarioEvaluator.SavingsGoalContext.noGoal(), 6, "USD"
        );
        assertFalse(usdResult.feasible());
        for (String w : usdResult.warnings()) {
            assertTrue(w.contains("$"));
            assertFalse(w.contains("₹"));
        }
        assertTrue(usdResult.warnings().stream().anyMatch(w -> w.contains("$500.00")));
    }

    @Test
    @DisplayName("WhatIf DiffSummary Generator - INR produces ₹ and no $, USD produces $")
    void testWhatIfDiffSummary_Currency() {
        DecisionAssistantService assistantService = new DecisionAssistantService();

        WhatIfResponse.ProjectionState base = new WhatIfResponse.ProjectionState(
                BigDecimal.valueOf(1000), BigDecimal.valueOf(1200), 20, "LOW", Collections.emptyList()
        );
        WhatIfResponse.ProjectionState sim = new WhatIfResponse.ProjectionState(
                BigDecimal.valueOf(500), BigDecimal.valueOf(700), 45, "MEDIUM", Collections.emptyList()
        );
        BigDecimal amount = BigDecimal.valueOf(500);

        // INR
        WhatIfResponse.DiffSummary inrDiff = assistantService.buildDiffSummary(base, sim, amount, "Buy Laptop", "INR");
        assertNotNull(inrDiff);
        assertFalse(inrDiff.impactSummaryText().contains("$"), "DiffSummary must not contain $ for INR: " + inrDiff.impactSummaryText());
        assertTrue(inrDiff.impactSummaryText().contains("₹"), "DiffSummary must contain ₹ for INR: " + inrDiff.impactSummaryText());
        // 500 * 83.5 = 41,750.00
        assertTrue(inrDiff.impactSummaryText().contains("₹41,750.00"));

        // USD
        WhatIfResponse.DiffSummary usdDiff = assistantService.buildDiffSummary(base, sim, amount, "Buy Laptop", "USD");
        assertNotNull(usdDiff);
        assertTrue(usdDiff.impactSummaryText().contains("$"), "DiffSummary must contain $ for USD: " + usdDiff.impactSummaryText());
        assertFalse(usdDiff.impactSummaryText().contains("₹"), "DiffSummary must not contain ₹ for USD: " + usdDiff.impactSummaryText());
        assertTrue(usdDiff.impactSummaryText().contains("$500.00"));
    }

    @Test
    @DisplayName("Savings Goal Indefinite Date Generator - INR produces ₹0.00 and no $, USD produces $0.00")
    void testSavingsGoalIndefiniteDate_Currency() {
        DecisionAssistantService assistantService = new DecisionAssistantService();

        SavingsGoal goal = new SavingsGoal();
        goal.setName("Vacation");
        goal.setTargetAmount(BigDecimal.valueOf(1000));
        goal.setCurrentAmount(BigDecimal.valueOf(100));

        // Incomes: 0, Expenses: 100 -> Monthly savings <= 0
        com.financeapp.model.Transaction exp = new com.financeapp.model.Transaction();
        exp.setAmount(BigDecimal.valueOf(100));
        exp.setCreatedAt(LocalDateTime.now());

        // INR
        GoalResponse inrGoal = assistantService.mapGoalToResponse(goal, Collections.emptyList(), List.of(exp), "INR");
        assertNotNull(inrGoal);
        assertFalse(inrGoal.estimatedCompletionDate().contains("$"), "Must not contain $ for INR: " + inrGoal.estimatedCompletionDate());
        assertTrue(inrGoal.estimatedCompletionDate().contains("₹0.00"), "Must contain ₹0.00 for INR: " + inrGoal.estimatedCompletionDate());

        // USD
        GoalResponse usdGoal = assistantService.mapGoalToResponse(goal, Collections.emptyList(), List.of(exp), "USD");
        assertNotNull(usdGoal);
        assertTrue(usdGoal.estimatedCompletionDate().contains("$0.00"), "Must contain $0.00 for USD: " + usdGoal.estimatedCompletionDate());
        assertFalse(usdGoal.estimatedCompletionDate().contains("₹"), "Must not contain ₹ for USD: " + usdGoal.estimatedCompletionDate());
    }
}
