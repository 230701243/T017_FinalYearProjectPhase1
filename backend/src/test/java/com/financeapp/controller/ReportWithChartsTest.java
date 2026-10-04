package com.financeapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financeapp.dto.MultiScenarioWhatIfRequest;
import com.financeapp.model.Income;
import com.financeapp.model.SavingsGoal;
import com.financeapp.model.Transaction;
import com.financeapp.model.User;
import com.financeapp.repository.IncomeRepository;
import com.financeapp.repository.SavingsGoalRepository;
import com.financeapp.repository.TransactionRepository;
import com.financeapp.repository.UserRepository;
import com.financeapp.service.CurrencyConversionService;
import com.financeapp.service.WhatIfSimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class ReportWithChartsTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IncomeRepository incomeRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired(required = false)
    private SavingsGoalRepository savingsGoalRepository;

    @Autowired
    private WhatIfSimulationService whatIfSimulationService;

    @Autowired
    private CurrencyConversionService currencyConversionService;

    private static final String TEST_USER = "chartReportTester";

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        incomeRepository.deleteAll();
        if (savingsGoalRepository != null) {
            savingsGoalRepository.deleteAll();
        }
        whatIfSimulationService.clearActiveSimulation(TEST_USER);

        // Ensure user preferred currency is INR for symbol testing
        userRepository.findByUsername(TEST_USER).ifPresentOrElse(u -> {
            u.setPreferredCurrency("INR");
            userRepository.save(u);
        }, () -> {
            User u = new User(TEST_USER, "test@financeapp.com", "hashpassword");
            u.setPreferredCurrency("INR");
            userRepository.save(u);
        });
    }

    @Test
    @WithMockUser(username = TEST_USER)
    @DisplayName("Verify report HTML contains all 11 sections in exact requested order with embedded SVG charts")
    void testMonthlyReportWithAllSectionsAndCharts() throws Exception {
        // Setup Ledger Data:
        // Income = ₹100,000
        Income inc = new Income(BigDecimal.valueOf(100000), "Monthly Tech Salary", "Salary", TEST_USER);
        inc.setCategory("Income & Salary");
        inc.setConfidenceSource("MANUAL");
        inc.setCreatedAt(LocalDateTime.now());
        incomeRepository.save(inc);

        // Expense 1 = ₹20,000 (Housing)
        Transaction exp1 = new Transaction();
        exp1.setAmount(BigDecimal.valueOf(20000));
        exp1.setCategory("Housing");
        exp1.setDescription("Rent");
        exp1.setPaymentMethod("Debit");
        exp1.setConfidenceSource("MANUAL");
        exp1.setUsername(TEST_USER);
        exp1.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(exp1);

        // Expense 2 = ₹10,000 (Food)
        Transaction exp2 = new Transaction();
        exp2.setAmount(BigDecimal.valueOf(10000));
        exp2.setCategory("Food & Dining");
        exp2.setDescription("Groceries");
        exp2.setPaymentMethod("Debit");
        exp2.setConfidenceSource("MANUAL");
        exp2.setUsername(TEST_USER);
        exp2.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(exp2);

        // Savings Goal = ₹50,000 target, ₹25,000 current
        if (savingsGoalRepository != null) {
            SavingsGoal goal = new SavingsGoal();
            goal.setName("Emergency Fund");
            goal.setTargetAmount(BigDecimal.valueOf(50000));
            goal.setCurrentAmount(BigDecimal.valueOf(25000));
            goal.setUsername(TEST_USER);
            goal.setCreatedAt(LocalDateTime.now());
            savingsGoalRepository.save(goal);
        }

        // Run active simulation
        MultiScenarioWhatIfRequest simReq = new MultiScenarioWhatIfRequest(
                BigDecimal.valueOf(40000),
                "New MacBook Pro",
                3,
                6,
                BigDecimal.valueOf(7500)
        );
        mockMvc.perform(post("/api/dashboard/what-if/multi-scenario")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(simReq)))
                .andExpect(status().isOk());

        assertTrue(whatIfSimulationService.hasActiveSimulation(TEST_USER), "User should have active simulation saved");

        // Download Report
        MvcResult reportResult = mockMvc.perform(get("/api/reports/monthly"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"FinanceApp_Monthly_Report.html\""))
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andReturn();

        String html = reportResult.getResponse().getContentAsString();
        assertNotNull(html);

        // 1. Check Section Order in HTML
        int idxNetBalance = html.indexOf("Net Balance");
        int idxMonthlyIncome = html.indexOf("Monthly Income");
        int idxMonthlyExpenses = html.indexOf("Monthly Expenses");
        int idxSavingsRatio = html.indexOf("Savings Ratio");
        int idxCategoryBreakdown = html.indexOf("Category-wise Expense Breakdown");
        int idxTopCategory = html.indexOf("Top Expense Category");
        int idxRiskScore = html.indexOf("Financial Risk Score");
        int idxForecast = html.indexOf("Next-Month Expense Forecast");
        int idxCashFlowChart = html.indexOf("Cash-Flow Projection Chart");
        int idxScenarioChart = html.indexOf("Scenario Comparison Chart");
        int idxSavingsGoal = html.indexOf("Active Savings Goals");

        assertTrue(idxNetBalance >= 0, "Net Balance section present");
        assertTrue(idxMonthlyIncome > idxNetBalance, "Monthly Income after Net Balance");
        assertTrue(idxMonthlyExpenses > idxMonthlyIncome, "Monthly Expenses after Monthly Income");
        assertTrue(idxSavingsRatio > idxMonthlyExpenses, "Savings Ratio after Monthly Expenses");
        assertTrue(idxCategoryBreakdown > idxSavingsRatio, "Category-wise Expense Breakdown after Savings Ratio");
        assertTrue(idxTopCategory > idxCategoryBreakdown, "Top Expense Category after Category Breakdown");
        assertTrue(idxRiskScore > idxTopCategory, "Financial Risk Score after Top Expense Category");
        assertTrue(idxForecast > idxRiskScore, "Next-Month Expense Forecast after Risk Score");
        assertTrue(idxCashFlowChart > idxForecast, "Cash-Flow Projection Chart after Forecast");
        assertTrue(idxScenarioChart > idxCashFlowChart, "Scenario Comparison Chart after Cash-Flow Projection");
        assertTrue(idxSavingsGoal > idxScenarioChart, "Active Savings Goals after Scenario Comparison");

        // 2. Confirm Cash-Flow Projection Chart has SVG elements
        assertTrue(html.contains("Cash-Flow Projection</text>"), "Cash-Flow chart title rendered");
        assertTrue(html.contains("Min Reserve"), "Cash-Flow chart has red dashed reference line for minimum reserve");
        assertTrue(html.contains("stroke-dasharray=\"6,4\""), "Dashed reference line rendered");
        assertTrue(html.contains("Projected Balance"), "Projected balance legend rendered");

        // 3. Confirm Scenario Comparison Chart has SVG elements and values
        assertTrue(html.contains("Scenario Comparison</text>"), "Scenario comparison chart title rendered");
        assertTrue(html.contains("Buy Now"), "Buy Now scenario bar present");
        assertTrue(html.contains("Wait (3m)"), "Wait scenario bar present");
        assertTrue(html.contains("EMI (6m)"), "EMI scenario bar present");
    }

    @Test
    @WithMockUser(username = TEST_USER)
    @DisplayName("Verify Scenario Comparison Chart is omitted when no active simulation exists, and Savings Goal is omitted when no goal is set")
    void testOmitOptionalSections() throws Exception {
        // Only 1 income, no simulation, no savings goals
        Income inc = new Income(BigDecimal.valueOf(50000), "Salary", "Salary", TEST_USER);
        inc.setCategory("Income & Salary");
        inc.setConfidenceSource("MANUAL");
        inc.setCreatedAt(LocalDateTime.now());
        incomeRepository.save(inc);

        MvcResult reportResult = mockMvc.perform(get("/api/reports/monthly"))
                .andExpect(status().isOk())
                .andReturn();

        String html = reportResult.getResponse().getContentAsString();

        // Cash flow projection must still be present
        assertTrue(html.contains("Cash-Flow Projection Chart"), "Cash-Flow Projection Chart must be present");

        // Scenario Comparison Chart MUST BE OMITTED
        assertFalse(html.contains("Scenario Comparison Chart"), "Scenario Comparison Chart must be omitted when no active simulation");

        // Savings Goal MUST BE OMITTED
        assertFalse(html.contains("Active Savings Goals"), "Active Savings Goals must be omitted when no goal is set");
    }
}
