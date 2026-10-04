package com.financeapp.service;

import com.financeapp.dto.FinancialRecommendationResponse;
import com.financeapp.dto.GoalResponse;
import com.financeapp.dto.RiskScoreResponse;
import com.financeapp.dto.ThreeMonthCashFlowResponse;
import com.financeapp.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RecommendationServiceTest {

    private RecommendationService recommendationService;

    @BeforeEach
    void setUp() {
        recommendationService = new RecommendationService();
    }

    private ThreeMonthCashFlowResponse buildSampleCashFlow() {
        // startBalance = 508.98, outflow = 59.88, reserveThreshold = 59.88 * 3 = 179.64
        // expectedMonthlyIncome = 598.80, outflow = 59.88, net savings rate = 538.92
        ThreeMonthCashFlowResponse.MonthlyCashFlowProjection p1 =
                new ThreeMonthCashFlowResponse.MonthlyCashFlowProjection(
                        "Month 1",
                        new BigDecimal("508.98"),
                        new BigDecimal("598.80"),
                        new BigDecimal("59.88"),
                        BigDecimal.ZERO,
                        new BigDecimal("538.92"),
                        new BigDecimal("1047.90")
                );

        return new ThreeMonthCashFlowResponse(
                new BigDecimal("508.98"),
                new BigDecimal("598.80"),
                BigDecimal.ZERO,
                List.of(p1)
        );
    }

    private RiskScoreResponse buildSampleRiskScore() {
        return new RiskScoreResponse(15, "LOW", List.of());
    }

    private GoalResponse buildSampleGoal() {
        return new GoalResponse(
                1L,
                "New Laptop",
                new BigDecimal("1200.00"),
                new BigDecimal("122.16"),
                new BigDecimal("1077.84"),
                10.2,
                2.0,
                "Est. Dec 2026 (~2.0 mos)",
                LocalDateTime.now()
        );
    }

    @Test
    @DisplayName("Evaluate recommendations with INR - No dollar signs, correct rupee values")
    void testEvaluateRecommendations_INR() {
        ThreeMonthCashFlowResponse cf = buildSampleCashFlow();
        RiskScoreResponse risk = buildSampleRiskScore();
        List<GoalResponse> goals = List.of(buildSampleGoal());

        FinancialRecommendationResponse response = recommendationService.evaluateRecommendations(cf, risk, goals, "INR");

        assertNotNull(response);
        assertNotNull(response.recommendationsList());
        assertTrue(response.recommendationsList().size() >= 3 && response.recommendationsList().size() <= 5);

        for (FinancialRecommendationResponse.RecommendationItem item : response.recommendationsList()) {
            assertFalse(item.title().contains("$"), "Title must not contain $ with INR: " + item.title());
            assertFalse(item.detail().contains("$"), "Detail must not contain $ with INR: " + item.detail());
            assertFalse(item.actionableStep().contains("$"), "ActionableStep must not contain $ with INR: " + item.actionableStep());
        }

        // Emergency Reserve Buffer check: 179.64 * 83.5 = 14,999.94, 508.98 * 83.5 = 42,499.83
        FinancialRecommendationResponse.RecommendationItem reserveItem = response.recommendationsList().stream()
                .filter(r -> "Emergency Reserve Buffer".equals(r.title()))
                .findFirst()
                .orElseThrow();

        assertTrue(reserveItem.detail().contains("₹14,999.94"), "Reserve detail should contain ₹14,999.94 but was: " + reserveItem.detail());
        assertTrue(reserveItem.detail().contains("₹42,499.83"), "Reserve detail should contain ₹42,499.83 but was: " + reserveItem.detail());
        assertFalse(reserveItem.detail().contains("$"));

        // Savings Goal check: net savings rate 538.92 * 83.5 = 44,999.82
        FinancialRecommendationResponse.RecommendationItem goalItem = response.recommendationsList().stream()
                .filter(r -> r.title().startsWith("Savings Goal"))
                .findFirst()
                .orElseThrow();

        assertTrue(goalItem.detail().contains("₹44,999.82/mo"), "Goal detail should contain ₹44,999.82/mo but was: " + goalItem.detail());
        assertTrue(goalItem.actionableStep().contains("₹44,999.82"), "Goal step should contain ₹44,999.82 but was: " + goalItem.actionableStep());
        assertFalse(goalItem.detail().contains("$"));
        assertFalse(goalItem.actionableStep().contains("$"));
    }

    @Test
    @DisplayName("Evaluate recommendations with USD - Dollar signs present with raw values")
    void testEvaluateRecommendations_USD() {
        ThreeMonthCashFlowResponse cf = buildSampleCashFlow();
        RiskScoreResponse risk = buildSampleRiskScore();
        List<GoalResponse> goals = List.of(buildSampleGoal());

        FinancialRecommendationResponse response = recommendationService.evaluateRecommendations(cf, risk, goals, "USD");

        assertNotNull(response);
        assertTrue(response.recommendationsList().size() >= 3 && response.recommendationsList().size() <= 5);

        for (FinancialRecommendationResponse.RecommendationItem item : response.recommendationsList()) {
            assertFalse(item.title().contains("₹"), "Title must not contain ₹ with USD: " + item.title());
            assertFalse(item.detail().contains("₹"), "Detail must not contain ₹ with USD: " + item.detail());
            assertFalse(item.actionableStep().contains("₹"), "ActionableStep must not contain ₹ with USD: " + item.actionableStep());
        }

        FinancialRecommendationResponse.RecommendationItem reserveItem = response.recommendationsList().stream()
                .filter(r -> "Emergency Reserve Buffer".equals(r.title()))
                .findFirst()
                .orElseThrow();

        assertTrue(reserveItem.detail().contains("$179.64"), "Reserve detail should contain $179.64 but was: " + reserveItem.detail());
        assertTrue(reserveItem.detail().contains("$508.98"), "Reserve detail should contain $508.98 but was: " + reserveItem.detail());

        FinancialRecommendationResponse.RecommendationItem goalItem = response.recommendationsList().stream()
                .filter(r -> r.title().startsWith("Savings Goal"))
                .findFirst()
                .orElseThrow();

        assertTrue(goalItem.detail().contains("$538.92/mo"), "Goal detail should contain $538.92/mo but was: " + goalItem.detail());
        assertTrue(goalItem.actionableStep().contains("$538.92"), "Goal step should contain $538.92 but was: " + goalItem.actionableStep());
    }

    @Test
    @DisplayName("Evaluate recommendations when below reserve and with deficit")
    void testEvaluateRecommendations_BelowReserveAndDeficit_INR_vs_USD() {
        ThreeMonthCashFlowResponse.MonthlyCashFlowProjection p1 =
                new ThreeMonthCashFlowResponse.MonthlyCashFlowProjection(
                        "Month 1",
                        new BigDecimal("100.00"),
                        new BigDecimal("50.00"),
                        new BigDecimal("200.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("-150.00"),
                        new BigDecimal("-50.00")
                );

        ThreeMonthCashFlowResponse cf = new ThreeMonthCashFlowResponse(
                new BigDecimal("100.00"),
                new BigDecimal("50.00"),
                BigDecimal.ZERO,
                List.of(p1)
        );

        RiskScoreResponse risk = new RiskScoreResponse(80, "HIGH", List.of());
        List<GoalResponse> goals = List.of(buildSampleGoal());

        // Test INR
        FinancialRecommendationResponse inrResp = recommendationService.evaluateRecommendations(cf, risk, goals, "INR");
        for (FinancialRecommendationResponse.RecommendationItem item : inrResp.recommendationsList()) {
            assertFalse(item.detail().contains("$"));
            assertFalse(item.actionableStep().contains("$"));
        }
        FinancialRecommendationResponse.RecommendationItem inrGoal = inrResp.recommendationsList().stream()
                .filter(r -> r.title().startsWith("Savings Goal")).findFirst().orElseThrow();
        // Deficit 150 * 83.5 = 12,525.00
        assertTrue(inrGoal.detail().contains("₹12,525.00/mo deficit"));
        assertTrue(inrGoal.actionableStep().contains("₹12,525.00 deficit"));

        // Test USD
        FinancialRecommendationResponse usdResp = recommendationService.evaluateRecommendations(cf, risk, goals, "USD");
        FinancialRecommendationResponse.RecommendationItem usdGoal = usdResp.recommendationsList().stream()
                .filter(r -> r.title().startsWith("Savings Goal")).findFirst().orElseThrow();
        assertTrue(usdGoal.detail().contains("$150.00/mo deficit"));
        assertTrue(usdGoal.actionableStep().contains("$150.00 deficit"));
    }

    @Test
    @DisplayName("Rule 1.a: Health Insurance Gap triggers when medical goal exists and no insurance transactions")
    void testHealthInsuranceGap_TriggersWhenNoInsuranceTransactions() {
        ThreeMonthCashFlowResponse cf = buildSampleCashFlow();
        RiskScoreResponse risk = buildSampleRiskScore();
        GoalResponse medicalGoal = new GoalResponse(
                2L,
                "Medical Emergency Reserve",
                new BigDecimal("5000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("4000.00"),
                20.0,
                12.0,
                "Est. Oct 2027",
                LocalDateTime.now()
        );

        // No insurance transactions, checkbox unchecked (false)
        FinancialRecommendationResponse resp = recommendationService.evaluateRecommendations(
                cf, risk, List.of(medicalGoal), "INR", Collections.emptyList(), Collections.emptyList(), false, false
        );

        FinancialRecommendationResponse.RecommendationItem insuranceRec = resp.recommendationsList().stream()
                .filter(r -> "Health Insurance Coverage Gap".equals(r.title()))
                .findFirst()
                .orElse(null);

        assertNotNull(insuranceRec, "Health Insurance recommendation should trigger");
        assertEquals("HIGH", insuranceRec.priority());
        assertEquals("Insurance", insuranceRec.category());
        assertTrue(insuranceRec.detail().contains("No insurance premium found in your recorded transactions"));
        assertFalse(insuranceRec.detail().toLowerCase().contains("you have no insurance"));
        assertEquals("https://pmjay.gov.in", insuranceRec.officialUrl());
    }

    @Test
    @DisplayName("Rule 1.a: Health Insurance Gap is suppressed when an insurance transaction exists")
    void testHealthInsuranceGap_SuppressedByTransaction() {
        ThreeMonthCashFlowResponse cf = buildSampleCashFlow();
        RiskScoreResponse risk = buildSampleRiskScore();
        GoalResponse medicalGoal = new GoalResponse(
                2L,
                "Medical Emergency",
                new BigDecimal("5000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("4000.00"),
                20.0,
                12.0,
                "Est. Oct 2027",
                LocalDateTime.now()
        );

        Transaction tx = new Transaction(new BigDecimal("100.00"), "LIC premium payment", "UPI", "testuser");
        tx.setCategory("Insurance");
        tx.setCreatedAt(LocalDateTime.now().minusMonths(2));

        FinancialRecommendationResponse resp = recommendationService.evaluateRecommendations(
                cf, risk, List.of(medicalGoal), "INR", List.of(tx), Collections.emptyList(), false, false
        );

        boolean hasInsuranceGap = resp.recommendationsList().stream()
                .anyMatch(r -> "Health Insurance Coverage Gap".equals(r.title()));

        assertFalse(hasInsuranceGap, "Health Insurance recommendation must be suppressed when insurance transaction exists");
    }

    @Test
    @DisplayName("Rule 1.a: Health Insurance Gap is suppressed when user profile checkbox is ticked")
    void testHealthInsuranceGap_SuppressedByCheckbox() {
        ThreeMonthCashFlowResponse cf = buildSampleCashFlow();
        RiskScoreResponse risk = buildSampleRiskScore();
        GoalResponse medicalGoal = new GoalResponse(
                2L,
                "Health & Medical Care",
                new BigDecimal("5000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("4000.00"),
                20.0,
                12.0,
                "Est. Oct 2027",
                LocalDateTime.now()
        );

        // Checkbox hasHealthInsurance = true
        FinancialRecommendationResponse resp = recommendationService.evaluateRecommendations(
                cf, risk, List.of(medicalGoal), "INR", Collections.emptyList(), Collections.emptyList(), true, false
        );

        boolean hasInsuranceGap = resp.recommendationsList().stream()
                .anyMatch(r -> "Health Insurance Coverage Gap".equals(r.title()));

        assertFalse(hasInsuranceGap, "Health Insurance recommendation must be suppressed when hasHealthInsurance is true");
    }

    @Test
    @DisplayName("Rule 2: Future expense planning for a goal due in 6 months with gap calculates real required monthly amount")
    void testFutureExpensePlanning_GoalDueIn6Months() {
        ThreeMonthCashFlowResponse cf = buildSampleCashFlow(); // surplus = 538.92
        RiskScoreResponse risk = buildSampleRiskScore();

        // Target = 1000 USD, Current = 200 USD -> gap = 800 USD. Target date = 6 months away.
        // Monthly needed = 800 / 6 = 133.33 USD/mo.
        // In INR: 133.33 * 83.50 = ₹11,133.06/mo. Surplus 538.92 * 83.5 = ₹44,999.82/mo. Surplus is enough!
        GoalResponse goalDueIn6M = new GoalResponse(
                3L,
                "Wedding Jewelry",
                new BigDecimal("1000.00"),
                new BigDecimal("200.00"),
                new BigDecimal("800.00"),
                20.0,
                6.0,
                "Est. Apr 2027",
                LocalDate.now().plusMonths(6),
                LocalDateTime.now()
        );

        FinancialRecommendationResponse resp = recommendationService.evaluateRecommendations(
                cf, risk, List.of(goalDueIn6M), "INR", Collections.emptyList(), Collections.emptyList(), false, false
        );

        FinancialRecommendationResponse.RecommendationItem futurePlan = resp.recommendationsList().stream()
                .filter(r -> r.title().startsWith("Future Expense Planning: Wedding Jewelry"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Future Expense Planning card should be present"));

        assertTrue(futurePlan.detail().contains("₹11,133.06/mo"));
        assertTrue(futurePlan.detail().contains("6 remaining months"));
        assertTrue(futurePlan.detail().contains("₹44,999.82/mo"));
        assertEquals("Savings", futurePlan.category());
        assertEquals("https://www.rbi.org.in", futurePlan.officialUrl());
    }

    @Test
    @DisplayName("Rule 2: User with no upcoming costs sees no future-expense card")
    void testFutureExpensePlanning_NoUpcomingCosts() {
        ThreeMonthCashFlowResponse cf = buildSampleCashFlow();
        RiskScoreResponse risk = buildSampleRiskScore();

        // Goals list is completely empty
        FinancialRecommendationResponse resp = recommendationService.evaluateRecommendations(
                cf, risk, Collections.emptyList(), "INR", Collections.emptyList(), Collections.emptyList(), false, false
        );

        boolean hasFutureCard = resp.recommendationsList().stream()
                .anyMatch(r -> r.title().startsWith("Future Expense Planning") || r.title().startsWith("Future Expense Shortfall"));

        assertFalse(hasFutureCard, "User with no upcoming costs must see no future-expense card");
    }

    @Test
    @DisplayName("Rule 4: Region-based scheme links appear for INR but are absent for USD")
    void testRegionBasedSchemeLinks_INR_vs_USD() {
        ThreeMonthCashFlowResponse cf = buildSampleCashFlow();
        RiskScoreResponse risk = buildSampleRiskScore();
        GoalResponse medicalGoal = new GoalResponse(
                2L,
                "Medical Emergency",
                new BigDecimal("5000.00"),
                new BigDecimal("1000.00"),
                new BigDecimal("4000.00"),
                20.0,
                12.0,
                "Est. Oct 2027",
                LocalDateTime.now()
        );

        // INR response
        FinancialRecommendationResponse inrResp = recommendationService.evaluateRecommendations(
                cf, risk, List.of(medicalGoal), "INR", Collections.emptyList(), Collections.emptyList(), false, false
        );
        FinancialRecommendationResponse.RecommendationItem inrIns = inrResp.recommendationsList().stream()
                .filter(r -> "Health Insurance Coverage Gap".equals(r.title()))
                .findFirst().orElseThrow();
        assertNotNull(inrIns.officialUrl(), "INR should have official scheme URL");
        assertTrue(inrIns.officialUrl().contains("pmjay.gov.in"));

        // USD response
        FinancialRecommendationResponse usdResp = recommendationService.evaluateRecommendations(
                cf, risk, List.of(medicalGoal), "USD", Collections.emptyList(), Collections.emptyList(), false, false
        );
        FinancialRecommendationResponse.RecommendationItem usdIns = usdResp.recommendationsList().stream()
                .filter(r -> "Health Insurance Coverage Gap".equals(r.title()))
                .findFirst().orElseThrow();
        assertNull(usdIns.officialUrl(), "USD user should not see India-specific scheme link");
        assertFalse(usdIns.actionableStep().contains("PM-JAY"), "USD user should not see PM-JAY mention");
    }

    @Test
    @DisplayName("Rule 5: Educational entry does NOT contain stock names, price targets, or predictions")
    void testEducationalEntry_NoStockNamesOrPredictions() {
        ThreeMonthCashFlowResponse cf = buildSampleCashFlow();
        RiskScoreResponse risk = buildSampleRiskScore();

        FinancialRecommendationResponse resp = recommendationService.evaluateRecommendations(
                cf, risk, Collections.emptyList(), "INR"
        );

        FinancialRecommendationResponse.RecommendationItem eduItem = resp.recommendationsList().stream()
                .filter(r -> "Investment Options to Learn About".equals(r.title()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Educational investment entry must be present"));

        assertTrue(eduItem.detail().contains("General information, not investment advice."));
        assertEquals("Savings", eduItem.category());

        String text = (eduItem.title() + " " + eduItem.detail() + " " + eduItem.actionableStep()).toLowerCase();
        assertFalse(text.contains("tcs"));
        assertFalse(text.contains("reliance"));
        assertFalse(text.contains("apple"));
        assertFalse(text.contains("tesla"));
        assertFalse(text.contains("target price"));
        assertFalse(text.contains("buy target"));
        assertFalse(text.contains("prediction"));
    }

    @Test
    @DisplayName("Rule 6: Card returns at most top 5 recommendations by priority")
    void testTop5RecommendationsLimit() {
        ThreeMonthCashFlowResponse cf = buildSampleCashFlow();
        RiskScoreResponse risk = buildSampleRiskScore();
        GoalResponse goal1 = new GoalResponse(1L, "Medical Emergency", new BigDecimal("1000.00"), new BigDecimal("100.00"), new BigDecimal("900.00"), 10.0, 6.0, "Est. Apr 2027", LocalDate.now().plusMonths(6), LocalDateTime.now());
        GoalResponse goal2 = new GoalResponse(2L, "Retirement Villa", new BigDecimal("50000.00"), new BigDecimal("5000.00"), new BigDecimal("45000.00"), 10.0, 60.0, "Est. Oct 2031", LocalDate.now().plusMonths(60), LocalDateTime.now());

        FinancialRecommendationResponse resp = recommendationService.evaluateRecommendations(
                cf, risk, List.of(goal1, goal2), "INR", Collections.emptyList(), Collections.emptyList(), false, false
        );

        assertNotNull(resp.recommendationsList());
        assertTrue(resp.recommendationsList().size() <= 5, "Recommendations list must not exceed 5 items but was: " + resp.recommendationsList().size());
    }
}
