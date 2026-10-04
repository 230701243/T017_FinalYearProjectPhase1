package com.financeapp.service;

import com.financeapp.dto.DecisionScenarioResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DecisionScenarioEvaluatorTest {

    @Nested
    @DisplayName("evaluateBuyNow Tests")
    class BuyNowTests {

        @Test
        @DisplayName("₹80,000 savings buy now case - should BREACH reserve floor")
        void testBuyNowWith80kSavings_ShouldBreach() {
            BigDecimal currentSavings = BigDecimal.valueOf(80000);
            BigDecimal purchaseCost = BigDecimal.valueOf(50000);
            BigDecimal reserveFloor = BigDecimal.valueOf(50000);

            DecisionScenarioResult result = DecisionScenarioEvaluator.evaluateBuyNow(currentSavings, purchaseCost, reserveFloor);

            assertNotNull(result);
            assertFalse(result.feasible(), "Should be not feasible because 80k - 50k = 30k < 50k reserve floor");
            assertEquals(0, BigDecimal.valueOf(30000.00).compareTo(result.projectedSavingsOrBudget()));
            assertFalse(result.warnings().isEmpty(), "Warnings should contain reserve floor breach message");
            assertTrue(result.warnings().get(0).contains("Reserve floor breached"));
            assertEquals(currentSavings, result.rawNumbers().get("currentSavings"));
            assertEquals(purchaseCost, result.rawNumbers().get("purchaseCost"));
            assertEquals(reserveFloor, result.rawNumbers().get("reserveFloor"));
        }

        @Test
        @DisplayName("₹200,000 savings buy now case - should NOT breach reserve floor")
        void testBuyNowWith200kSavings_ShouldNotBreach() {
            BigDecimal currentSavings = BigDecimal.valueOf(200000);
            BigDecimal purchaseCost = BigDecimal.valueOf(50000);
            BigDecimal reserveFloor = BigDecimal.valueOf(50000);

            DecisionScenarioResult result = DecisionScenarioEvaluator.evaluateBuyNow(currentSavings, purchaseCost, reserveFloor);

            assertNotNull(result);
            assertTrue(result.feasible(), "Should be feasible because 200k - 50k = 150k >= 50k reserve floor");
            assertEquals(0, BigDecimal.valueOf(150000.00).compareTo(result.projectedSavingsOrBudget()));
            assertTrue(result.warnings().isEmpty(), "No warnings expected on successful evaluation");
        }

        @Test
        @DisplayName("Insufficient savings buy now - should breach due to negative projected balance")
        void testBuyNowInsufficientFunds_ShouldBreach() {
            BigDecimal currentSavings = BigDecimal.valueOf(30000);
            BigDecimal purchaseCost = BigDecimal.valueOf(50000);
            BigDecimal reserveFloor = BigDecimal.valueOf(20000);

            DecisionScenarioResult result = DecisionScenarioEvaluator.evaluateBuyNow(currentSavings, purchaseCost, reserveFloor);

            assertNotNull(result);
            assertFalse(result.feasible());
            assertEquals(0, BigDecimal.valueOf(-20000.00).compareTo(result.projectedSavingsOrBudget()));
            assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Insufficient funds")));
        }
    }

    @Nested
    @DisplayName("evaluateWaitOption Tests")
    class WaitOptionTests {

        @Test
        @DisplayName("Wait option with healthy 6-month net savings history - NO BREACH (Feasible)")
        void testWaitOption_HealthySavingsRate_Feasible() {
            BigDecimal currentSavings = BigDecimal.valueOf(80000);
            BigDecimal purchaseCost = BigDecimal.valueOf(100000);
            BigDecimal reserveFloor = BigDecimal.valueOf(50000);

            // Actual historical 6-month net savings (income minus expenses)
            List<BigDecimal> monthlyNetSavingsHistory = List.of(
                    BigDecimal.valueOf(18000),
                    BigDecimal.valueOf(22000),
                    BigDecimal.valueOf(20000),
                    BigDecimal.valueOf(19000),
                    BigDecimal.valueOf(21000),
                    BigDecimal.valueOf(20000)
            ); // Avg: 20,000 / mo

            // Deficit = (100,000 + 50,000) - 80,000 = 70,000
            // Months to wait = ceil(70,000 / 20,000) = 4 months
            DecisionScenarioResult result = DecisionScenarioEvaluator.evaluateWaitOption(
                    currentSavings, purchaseCost, reserveFloor, monthlyNetSavingsHistory
            );

            assertNotNull(result);
            assertTrue(result.feasible(), "Wait option should be feasible with 4 months wait");
            assertEquals(4, result.rawNumbers().get("monthsToWait"));
            assertEquals(0, BigDecimal.valueOf(20000.00).compareTo((BigDecimal) result.rawNumbers().get("avgMonthlyNetSavings")));
            assertTrue(result.warnings().isEmpty());
            // After 4 months: 80,000 + (4 * 20,000) = 160,000; after purchase: 160,000 - 100,000 = 60,000 >= 50,000 floor
            assertEquals(0, BigDecimal.valueOf(60000.00).compareTo(result.projectedSavingsOrBudget()));
        }

        @Test
        @DisplayName("Wait option with negative/zero historical net savings - BREACH (Infeasible)")
        void testWaitOption_NegativeHistoricalSavingsRate_Breach() {
            BigDecimal currentSavings = BigDecimal.valueOf(40000);
            BigDecimal purchaseCost = BigDecimal.valueOf(100000);
            BigDecimal reserveFloor = BigDecimal.valueOf(50000);

            // Actual historical 6-month net savings where expenses exceeded income
            List<BigDecimal> monthlyNetSavingsHistory = List.of(
                    BigDecimal.valueOf(-5000),
                    BigDecimal.valueOf(-2000),
                    BigDecimal.valueOf(0),
                    BigDecimal.valueOf(-3000),
                    BigDecimal.valueOf(-4000),
                    BigDecimal.valueOf(-1000)
            );

            DecisionScenarioResult result = DecisionScenarioEvaluator.evaluateWaitOption(
                    currentSavings, purchaseCost, reserveFloor, monthlyNetSavingsHistory
            );

            assertNotNull(result);
            assertFalse(result.feasible(), "Should be infeasible due to negative historical net savings");
            assertEquals(-1, result.rawNumbers().get("monthsToWait"));
            assertTrue(result.warnings().stream().anyMatch(w -> w.contains("non-positive")));
        }

        @Test
        @DisplayName("Wait option with long horizon (> 12 months) - BREACH")
        void testWaitOption_LongHorizon_Breach() {
            BigDecimal currentSavings = BigDecimal.valueOf(10000);
            BigDecimal purchaseCost = BigDecimal.valueOf(200000);
            BigDecimal reserveFloor = BigDecimal.valueOf(50000);

            // 6-month history with low savings rate of 5,000 / mo
            List<BigDecimal> monthlyNetSavingsHistory = List.of(
                    BigDecimal.valueOf(5000),
                    BigDecimal.valueOf(5000),
                    BigDecimal.valueOf(5000),
                    BigDecimal.valueOf(5000),
                    BigDecimal.valueOf(5000),
                    BigDecimal.valueOf(5000)
            );
            // Deficit = 240,000 / 5,000 = 48 months > 12

            DecisionScenarioResult result = DecisionScenarioEvaluator.evaluateWaitOption(
                    currentSavings, purchaseCost, reserveFloor, monthlyNetSavingsHistory
            );

            assertNotNull(result);
            assertFalse(result.feasible());
            assertEquals(48, result.rawNumbers().get("monthsToWait"));
            assertTrue(result.warnings().stream().anyMatch(w -> w.contains("exceeds the 12-month short-term horizon")));
        }
    }

    @Nested
    @DisplayName("evaluateEmiOption Tests")
    class EmiOptionTests {

        @Test
        @DisplayName("EMI Option within limits - NO BREACH (Feasible)")
        void testEmiOption_WithinLimits_Feasible() {
            BigDecimal purchaseCost = BigDecimal.valueOf(90000);
            BigDecimal emiAmount = BigDecimal.valueOf(15000);
            BigDecimal salary = BigDecimal.valueOf(100000);
            BigDecimal fixedExpenses = BigDecimal.valueOf(40000);
            BigDecimal minSavingsGoal = BigDecimal.valueOf(20000);

            // EMI ratio: 15,000 / 100,000 = 15.0% <= 30.0%
            // Remaining budget: 100,000 - 40,000 - 20,000 - 15,000 = 25,000 > 0
            DecisionScenarioResult result = DecisionScenarioEvaluator.evaluateEmiOption(
                    purchaseCost, emiAmount, salary, fixedExpenses, minSavingsGoal
            );

            assertNotNull(result);
            assertTrue(result.feasible(), "EMI option should be feasible");
            assertEquals(0, BigDecimal.valueOf(25000.00).compareTo(result.projectedSavingsOrBudget()));
            assertTrue(result.warnings().isEmpty());
        }

        @Test
        @DisplayName("EMI Option exceeding affordability ratio limit (35% > 30%) - BREACH")
        void testEmiOption_ExceedsAffordabilityRatio_Breach() {
            BigDecimal purchaseCost = BigDecimal.valueOf(120000);
            BigDecimal emiAmount = BigDecimal.valueOf(35000);
            BigDecimal salary = BigDecimal.valueOf(100000);
            BigDecimal fixedExpenses = BigDecimal.valueOf(30000);
            BigDecimal minSavingsGoal = BigDecimal.valueOf(10000);

            // EMI ratio: 35,000 / 100,000 = 35.0% > 30.0% limit
            DecisionScenarioResult result = DecisionScenarioEvaluator.evaluateEmiOption(
                    purchaseCost, emiAmount, salary, fixedExpenses, minSavingsGoal
            );

            assertNotNull(result);
            assertFalse(result.feasible(), "EMI option should breach 30% affordability ratio");
            assertTrue(result.warnings().stream().anyMatch(w -> w.contains("EMI affordability breached")));
        }

        @Test
        @DisplayName("EMI Option causing monthly budget deficit - BREACH")
        void testEmiOption_BudgetDeficit_Breach() {
            BigDecimal purchaseCost = BigDecimal.valueOf(60000);
            BigDecimal emiAmount = BigDecimal.valueOf(10000);
            BigDecimal salary = BigDecimal.valueOf(50000);
            BigDecimal fixedExpenses = BigDecimal.valueOf(30000);
            BigDecimal minSavingsGoal = BigDecimal.valueOf(15000);

            // Remaining budget: 50,000 - 30,000 - 15,000 - 10,000 = -5,000 (< 0)
            DecisionScenarioResult result = DecisionScenarioEvaluator.evaluateEmiOption(
                    purchaseCost, emiAmount, salary, fixedExpenses, minSavingsGoal
            );

            assertNotNull(result);
            assertFalse(result.feasible(), "EMI option should breach due to budget deficit");
            assertEquals(0, BigDecimal.valueOf(-5000.00).compareTo(result.projectedSavingsOrBudget()));
            assertTrue(result.warnings().stream().anyMatch(w -> w.contains("Budget deficit")));
        }

        @Test
        @DisplayName("Manually entered tenure of 7 months and EMI of ₹9,500 - no validation error & tenure-independent ratio")
        void testEmiOption_ManualTenure7MonthsAndEmi9500_Feasible() {
            BigDecimal purchaseCost = BigDecimal.valueOf(60000);
            BigDecimal manualEmiAmount = BigDecimal.valueOf(9500); // Manual EMI includes interest
            int manualTenureMonths = 7; // Custom 7-month tenure
            BigDecimal salary = BigDecimal.valueOf(50000);
            BigDecimal fixedExpenses = BigDecimal.valueOf(15000);
            BigDecimal minSavingsGoal = BigDecimal.valueOf(5000);
            BigDecimal emiAffordabilityLimit = BigDecimal.valueOf(0.30); // 30% max

            // 1. Evaluate with 7 months tenure
            DecisionScenarioResult result7 = DecisionScenarioEvaluator.evaluateEmiOption(
                    purchaseCost, manualEmiAmount, salary, fixedExpenses, minSavingsGoal,
                    emiAffordabilityLimit, DecisionScenarioEvaluator.SavingsGoalContext.noGoal(), manualTenureMonths
            );

            assertNotNull(result7);
            assertTrue(result7.feasible(), "Should be feasible: 9,500 / 50,000 = 19% <= 30%, remaining budget 50k-15k-5k-9.5k = 20.5k > 0");
            assertTrue(result7.warnings().isEmpty(), "No validation errors or breach warnings should occur for 7 months tenure");
            assertEquals(7, result7.rawNumbers().get("tenureMonths"));
            assertEquals(manualEmiAmount, result7.rawNumbers().get("emiAmount"));

            // 2. Evaluate with 12 months tenure with same manual EMI amount (₹9,500)
            DecisionScenarioResult result12 = DecisionScenarioEvaluator.evaluateEmiOption(
                    purchaseCost, manualEmiAmount, salary, fixedExpenses, minSavingsGoal,
                    emiAffordabilityLimit, DecisionScenarioEvaluator.SavingsGoalContext.noGoal(), 12
            );

            // Confirm emiAffordabilityRatio is identical (19.0%) and unaffected by tenure change
            assertEquals(result7.rawNumbers().get("emiToSalaryRatio"), result12.rawNumbers().get("emiToSalaryRatio"));
            assertEquals(0, BigDecimal.valueOf(0.1900).compareTo((BigDecimal) result7.rawNumbers().get("emiToSalaryRatio")));
            assertEquals(result7.projectedSavingsOrBudget(), result12.projectedSavingsOrBudget());
        }

        @Test
        @DisplayName("EMI Tenure range validation (clamping 1-60 and out-of-range warnings)")
        void testEmiOption_TenureRangeValidation() {
            BigDecimal purchaseCost = BigDecimal.valueOf(30000);
            BigDecimal salary = BigDecimal.valueOf(60000);
            BigDecimal fixedExpenses = BigDecimal.valueOf(20000);

            // Out of range: 0 months -> clamped to 1 with warning
            DecisionScenarioResult resLow = DecisionScenarioEvaluator.evaluateEmiOption(
                    purchaseCost, null, salary, fixedExpenses, BigDecimal.ZERO,
                    BigDecimal.valueOf(0.30), DecisionScenarioEvaluator.SavingsGoalContext.noGoal(), 0
            );
            assertEquals(1, resLow.rawNumbers().get("tenureMonths"));
            assertTrue(resLow.warnings().stream().anyMatch(w -> w.contains("Minimum allowed tenure is 1 month")));

            // Out of range: 72 months -> clamped to 60 with warning
            DecisionScenarioResult resHigh = DecisionScenarioEvaluator.evaluateEmiOption(
                    purchaseCost, null, salary, fixedExpenses, BigDecimal.ZERO,
                    BigDecimal.valueOf(0.30), DecisionScenarioEvaluator.SavingsGoalContext.noGoal(), 72
            );
            assertEquals(60, resHigh.rawNumbers().get("tenureMonths"));
            assertTrue(resHigh.warnings().stream().anyMatch(w -> w.contains("Maximum allowed tenure is 60 months")));
        }
    }

    @Nested
    @DisplayName("evaluateCustomTimingOption Tests")
    class CustomTimingOptionTests {

        @Test
        @DisplayName("Custom timing 3 months out with reserve breach vs Dynamic Wait 7 months safe")
        void testCustomTiming3MonthsBreachVsDynamicWait7MonthsSafe() {
            BigDecimal currentSavings = BigDecimal.valueOf(40000);
            BigDecimal purchaseCost = BigDecimal.valueOf(50000);
            BigDecimal reserveFloor = BigDecimal.valueOf(30000);
            List<BigDecimal> netSavingsHistory = List.of(
                    BigDecimal.valueOf(6000),
                    BigDecimal.valueOf(6000),
                    BigDecimal.valueOf(6000)
            ); // Avg: 6,000 / mo

            // 1. Evaluate Custom Timing at 3 months out:
            // 40,000 + (3 * 6,000) = 58,000. After purchase: 58,000 - 50,000 = 8,000 (< 30,000 floor -> Breach of 22,000)
            DecisionScenarioResult customTimingResult = DecisionScenarioEvaluator.evaluateCustomTimingOption(
                    currentSavings, purchaseCost, reserveFloor, netSavingsHistory, 3
            );

            assertNotNull(customTimingResult);
            assertFalse(customTimingResult.feasible(), "Custom timing at 3 months must be NOT feasible due to reserve floor breach");
            assertEquals(0, BigDecimal.valueOf(8000.00).compareTo(customTimingResult.projectedSavingsOrBudget()));
            assertFalse(customTimingResult.warnings().isEmpty());
            assertTrue(customTimingResult.warnings().get(0).contains("Reserve floor breached"));
            assertEquals(3, customTimingResult.rawNumbers().get("targetMonthsInFuture"));
            assertEquals(0, BigDecimal.valueOf(-22000.00).compareTo((BigDecimal) customTimingResult.rawNumbers().get("bufferAboveFloor")));

            // 2. Evaluate Dynamic Earliest-Safe Wait Option:
            // Deficit = (50,000 + 30,000) - 40,000 = 40,000. ceil(40,000 / 6,000) = 7 months
            DecisionScenarioResult dynamicWaitResult = DecisionScenarioEvaluator.evaluateWaitOption(
                    currentSavings, purchaseCost, reserveFloor, netSavingsHistory
            );

            assertNotNull(dynamicWaitResult);
            assertTrue(dynamicWaitResult.feasible(), "Dynamic wait option must be feasible at the earliest safe month");
            assertEquals(7, dynamicWaitResult.rawNumbers().get("monthsToWait"));
            assertTrue(dynamicWaitResult.warnings().isEmpty(), "Dynamic wait option should have no warnings");
            // After 7 months: 40,000 + (7 * 6,000) - 50,000 = 32,000 (>= 30,000 floor)
            assertEquals(0, BigDecimal.valueOf(32000.00).compareTo(dynamicWaitResult.projectedSavingsOrBudget()));
        }

        @Test
        @DisplayName("Custom timing within bounds (feasible at 8 months)")
        void testCustomTimingFeasibleAt8Months() {
            BigDecimal currentSavings = BigDecimal.valueOf(40000);
            BigDecimal purchaseCost = BigDecimal.valueOf(50000);
            BigDecimal reserveFloor = BigDecimal.valueOf(30000);
            List<BigDecimal> netSavingsHistory = List.of(BigDecimal.valueOf(6000));

            // At 8 months: 40,000 + (8 * 6,000) - 50,000 = 38,000 >= 30,000 floor
            DecisionScenarioResult result = DecisionScenarioEvaluator.evaluateCustomTimingOption(
                    currentSavings, purchaseCost, reserveFloor, netSavingsHistory, 8
            );

            assertNotNull(result);
            assertTrue(result.feasible());
            assertEquals(0, BigDecimal.valueOf(38000.00).compareTo(result.projectedSavingsOrBudget()));
            assertTrue(result.warnings().isEmpty());
        }

        @Test
        @DisplayName("Custom timing range clamping (past month clamped to 0, >24 clamped to 24)")
        void testCustomTimingRangeClamping() {
            BigDecimal currentSavings = BigDecimal.valueOf(50000);
            BigDecimal purchaseCost = BigDecimal.valueOf(20000);
            BigDecimal reserveFloor = BigDecimal.valueOf(10000);
            List<BigDecimal> history = List.of(BigDecimal.valueOf(5000));

            // Past month -2 -> clamped to 0
            DecisionScenarioResult past = DecisionScenarioEvaluator.evaluateCustomTimingOption(
                    currentSavings, purchaseCost, reserveFloor, history, -2
            );
            assertEquals(0, past.rawNumbers().get("targetMonthsInFuture"));
            assertTrue(past.warnings().stream().anyMatch(w -> w.contains("in the past")));

            // >24 months (36) -> clamped to 24
            DecisionScenarioResult future = DecisionScenarioEvaluator.evaluateCustomTimingOption(
                    currentSavings, purchaseCost, reserveFloor, history, 36
            );
            assertEquals(24, future.rawNumbers().get("targetMonthsInFuture"));
            assertTrue(future.warnings().stream().anyMatch(w -> w.contains("exceeds the 24-month horizon")));
        }
    }

    @Nested
    @DisplayName("Savings Goal Status Branching Tests")
    class SavingsGoalBranchingTests {

        @Test
        @DisplayName("Case (a): No goal exists — purchase evaluated on reserve floor only")
        void testCaseA_NoGoalExists_EvaluatedOnReserveFloorOnly() {
            BigDecimal currentSavings = BigDecimal.valueOf(100000);
            BigDecimal purchaseCost = BigDecimal.valueOf(40000);
            BigDecimal reserveFloor = BigDecimal.valueOf(50000);
            DecisionScenarioEvaluator.SavingsGoalContext noGoal = DecisionScenarioEvaluator.SavingsGoalContext.noGoal();
            BigDecimal avgMonthlyNetSavings = BigDecimal.valueOf(15000);

            // 1. Buy Now: 100k - 40k = 60k >= 50k floor -> Feasible, goalStatus = NO_GOAL
            DecisionScenarioResult buyNow = DecisionScenarioEvaluator.evaluateBuyNow(
                    currentSavings, purchaseCost, reserveFloor, noGoal, avgMonthlyNetSavings
            );
            assertTrue(buyNow.feasible(), "Buy now should be feasible when remaining savings >= reserve floor");
            assertEquals("NO_GOAL", buyNow.rawNumbers().get("goalStatus"));
            assertNull(buyNow.rawNumbers().get("goalName"));
            assertNull(buyNow.rawNumbers().get("goalDelayMonths"));
            assertTrue(buyNow.warnings().isEmpty(), "No goal warnings should be emitted");

            // 2. Wait Option: deficit = 0 (100k >= 90k required) -> 0 months wait, goalStatus = NO_GOAL
            List<BigDecimal> history = List.of(BigDecimal.valueOf(15000), BigDecimal.valueOf(15000));
            DecisionScenarioResult waitOpt = DecisionScenarioEvaluator.evaluateWaitOption(
                    currentSavings, purchaseCost, reserveFloor, history, noGoal
            );
            assertTrue(waitOpt.feasible());
            assertEquals("NO_GOAL", waitOpt.rawNumbers().get("goalStatus"));
            assertNull(waitOpt.rawNumbers().get("goalName"));

            // 3. EMI Option: evaluates strictly on standard affordability limits and remaining budget
            DecisionScenarioResult emi = DecisionScenarioEvaluator.evaluateEmiOption(
                    purchaseCost, BigDecimal.valueOf(6666.67), BigDecimal.valueOf(80000),
                    BigDecimal.valueOf(30000), BigDecimal.valueOf(16000), BigDecimal.valueOf(0.30),
                    noGoal, 6
            );
            assertTrue(emi.feasible());
            assertEquals("NO_GOAL", emi.rawNumbers().get("goalStatus"));
            assertNull(emi.rawNumbers().get("goalName"));
        }

        @Test
        @DisplayName("Case (b): Goal exists and not yet met — purchase evaluated against goal delay/progress")
        void testCaseB_GoalExistsAndNotYetMet_EvaluatedAgainstGoalDelay() {
            BigDecimal currentSavings = BigDecimal.valueOf(100000);
            BigDecimal purchaseCost = BigDecimal.valueOf(30000);
            BigDecimal reserveFloor = BigDecimal.valueOf(50000);
            BigDecimal avgMonthlyNetSavings = BigDecimal.valueOf(10000);

            // Target: 200k, Current: 80k -> Remaining: 120k (Not yet met)
            DecisionScenarioEvaluator.SavingsGoalContext goalNotMet = DecisionScenarioEvaluator.SavingsGoalContext.of(
                    "New Car", BigDecimal.valueOf(200000), BigDecimal.valueOf(80000)
            );
            assertTrue(goalNotMet.hasGoal());
            assertFalse(goalNotMet.isAlreadyMet());
            assertEquals(0, BigDecimal.valueOf(120000.00).compareTo(goalNotMet.remainingAmount()));

            // 1. Buy Now:
            // Baseline months = 120k / 10k = 12.0 mos
            // Delay months = 30k / 10k = 3.0 mos
            // Projected months = 15.0 mos
            DecisionScenarioResult buyNow = DecisionScenarioEvaluator.evaluateBuyNow(
                    currentSavings, purchaseCost, reserveFloor, goalNotMet, avgMonthlyNetSavings
            );
            assertEquals("NOT_MET", buyNow.rawNumbers().get("goalStatus"));
            assertEquals("New Car", buyNow.rawNumbers().get("goalName"));
            assertEquals(0, BigDecimal.valueOf(120000.00).compareTo((BigDecimal) buyNow.rawNumbers().get("goalRemainingAmount")));
            assertEquals(12.0, (Double) buyNow.rawNumbers().get("goalBaselineMonthsToComplete"), 0.01);
            assertEquals(3.0, (Double) buyNow.rawNumbers().get("goalDelayMonths"), 0.01);
            assertEquals(15.0, (Double) buyNow.rawNumbers().get("goalProjectedMonthsToComplete"), 0.01);
            assertNotNull(buyNow.rawNumbers().get("goalProjectedCompletionDate"));
            assertTrue(buyNow.warnings().stream().anyMatch(w -> w.contains("Goal delay") && w.contains("New Car")));

            // 2. Wait Option:
            // Target required: 30k + 50k = 80k <= 100k current savings -> deficit = 0, monthsToWait = 0
            List<BigDecimal> history = List.of(BigDecimal.valueOf(10000), BigDecimal.valueOf(10000));
            DecisionScenarioResult waitOpt = DecisionScenarioEvaluator.evaluateWaitOption(
                    currentSavings, purchaseCost, reserveFloor, history, goalNotMet
            );
            assertEquals("NOT_MET", waitOpt.rawNumbers().get("goalStatus"));
            assertEquals("New Car", waitOpt.rawNumbers().get("goalName"));
            assertEquals(0, (Integer) waitOpt.rawNumbers().get("monthsToWait"));
            assertEquals(0.0, (Double) waitOpt.rawNumbers().get("goalDelayMonths"), 0.01);
            assertNotNull(waitOpt.rawNumbers().get("goalProjectedCompletionDate"));

            // 3. EMI Option:
            // Salary: 60k, Fixed: 20k -> Monthly net savings before EMI = 40k
            // EMI: 5k/mo for 6 months -> Monthly net savings during EMI = 35k
            // Baseline months = 120k / 40k = 3.0 mos
            // Savings during tenure (6 * 35k = 210k >= 120k) -> Projected = 120k / 35k = 3.43 mos
            DecisionScenarioResult emi = DecisionScenarioEvaluator.evaluateEmiOption(
                    purchaseCost, BigDecimal.valueOf(5000), BigDecimal.valueOf(60000),
                    BigDecimal.valueOf(20000), BigDecimal.valueOf(10000), BigDecimal.valueOf(0.30),
                    goalNotMet, 6
            );
            assertEquals("NOT_MET", emi.rawNumbers().get("goalStatus"));
            assertEquals("New Car", emi.rawNumbers().get("goalName"));
            assertEquals(3.0, (Double) emi.rawNumbers().get("goalBaselineMonthsToComplete"), 0.01);
            assertTrue((Double) emi.rawNumbers().get("goalDelayMonths") > 0);
            assertNotNull(emi.rawNumbers().get("goalProjectedCompletionDate"));
        }

        @Test
        @DisplayName("Case (c): Goal exists and already exceeded — purchase evaluated as not threatening the goal")
        void testCaseC_GoalExistsAndAlreadyExceeded_EvaluatedAsNotThreatening() {
            BigDecimal currentSavings = BigDecimal.valueOf(150000);
            BigDecimal purchaseCost = BigDecimal.valueOf(40000);
            BigDecimal reserveFloor = BigDecimal.valueOf(50000);
            BigDecimal avgMonthlyNetSavings = BigDecimal.valueOf(20000);

            // Target: 100k, Current: 120k -> Remaining: 0 (Already met / exceeded)
            DecisionScenarioEvaluator.SavingsGoalContext goalMet = DecisionScenarioEvaluator.SavingsGoalContext.of(
                    "Emergency Fund", BigDecimal.valueOf(100000), BigDecimal.valueOf(120000)
            );
            assertTrue(goalMet.hasGoal());
            assertTrue(goalMet.isAlreadyMet());
            assertEquals(0, BigDecimal.ZERO.compareTo(goalMet.remainingAmount()));

            // 1. Buy Now: 150k - 40k = 110k >= 50k floor -> Feasible, goalStatus = ALREADY_MET, no goal delay warning
            DecisionScenarioResult buyNow = DecisionScenarioEvaluator.evaluateBuyNow(
                    currentSavings, purchaseCost, reserveFloor, goalMet, avgMonthlyNetSavings
            );
            assertTrue(buyNow.feasible());
            assertEquals("ALREADY_MET", buyNow.rawNumbers().get("goalStatus"));
            assertEquals("Emergency Fund", buyNow.rawNumbers().get("goalName"));
            assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal) buyNow.rawNumbers().get("goalRemainingAmount")));
            assertTrue(buyNow.warnings().isEmpty(), "Should not warn about goal delay when goal is already met/exceeded");
            String summary = (String) buyNow.rawNumbers().get("goalImpactSummary");
            assertNotNull(summary);
            assertTrue(summary.contains("already met/exceeded") && summary.contains("without threatening the goal"));

            // 2. Wait Option: goalStatus = ALREADY_MET, no delay impact
            List<BigDecimal> history = List.of(BigDecimal.valueOf(20000), BigDecimal.valueOf(20000));
            DecisionScenarioResult waitOpt = DecisionScenarioEvaluator.evaluateWaitOption(
                    currentSavings, purchaseCost, reserveFloor, history, goalMet
            );
            assertTrue(waitOpt.feasible());
            assertEquals("ALREADY_MET", waitOpt.rawNumbers().get("goalStatus"));
            assertEquals("Emergency Fund", waitOpt.rawNumbers().get("goalName"));
            assertTrue(waitOpt.warnings().isEmpty());

            // 3. EMI Option: goalStatus = ALREADY_MET, no goal pause warning
            DecisionScenarioResult emi = DecisionScenarioEvaluator.evaluateEmiOption(
                    purchaseCost, BigDecimal.valueOf(6666.67), BigDecimal.valueOf(80000),
                    BigDecimal.valueOf(30000), BigDecimal.ZERO, BigDecimal.valueOf(0.30),
                    goalMet, 6
            );
            assertTrue(emi.feasible());
            assertEquals("ALREADY_MET", emi.rawNumbers().get("goalStatus"));
            assertEquals("Emergency Fund", emi.rawNumbers().get("goalName"));
            assertTrue(emi.warnings().isEmpty());
        }
    }
}
