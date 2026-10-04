package com.financeapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financeapp.dto.MultiScenarioWhatIfRequest;
import com.financeapp.dto.MultiScenarioWhatIfResponse;
import com.financeapp.model.Income;
import com.financeapp.model.Transaction;
import com.financeapp.repository.IncomeRepository;
import com.financeapp.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class WhatIfMultiScenarioTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IncomeRepository incomeRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @WithMockUser(username = "whatIfUser")
    @DisplayName("Verify What-If Simulator uses manual EMI amount ₹9,500 in affordability ratio calculation, not cost/tenure")
    void testManualEmiAmountUsedInAffordabilityCheck() throws Exception {
        String username = "whatIfUser";

        // Setup: Salary = ₹50,000, Expense = ₹10,000
        Income income = new Income(BigDecimal.valueOf(50000), "Salary", "Salary", username);
        income.setCategory("Salary");
        income.setConfidenceSource("MANUAL");
        income.setCreatedAt(LocalDateTime.now());
        incomeRepository.save(income);

        Transaction expense = new Transaction();
        expense.setAmount(BigDecimal.valueOf(10000));
        expense.setCategory("Bills");
        expense.setDescription("Utilities");
        expense.setUsername(username);
        expense.setPaymentMethod("Debit");
        expense.setConfidenceSource("MANUAL");
        expense.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(expense);

        // Request: Cost ₹50,000, Tenure 6 mos, manually entered EMI ₹9,500
        MultiScenarioWhatIfRequest request = new MultiScenarioWhatIfRequest(
                BigDecimal.valueOf(50000),
                "Work Laptop",
                3,
                6,
                BigDecimal.valueOf(9500)
        );

        MvcResult result = mockMvc.perform(post("/api/dashboard/what-if/multi-scenario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        MultiScenarioWhatIfResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), MultiScenarioWhatIfResponse.class);

        assertNotNull(response);
        // 1. Confirm monthly EMI in response is exactly ₹9,500 (NOT 50,000 / 6 = 8,333.33)
        assertEquals(0, BigDecimal.valueOf(9500.00).compareTo(response.emiMonthlyAmount()));

        // 2. Confirm emiEvaluation exists and uses exactly ₹9,500 in affordability ratio check
        assertNotNull(response.emiEvaluation(), "emiEvaluation should be included in MultiScenarioWhatIfResponse");
        assertEquals(0, BigDecimal.valueOf(9500.00).compareTo(new BigDecimal(response.emiEvaluation().rawNumbers().get("emiAmount").toString())));

        // 3. Confirm affordability ratio is 9,500 / 50,000 = 0.1900 (19.0%), NOT 8,333.33 / 50,000 = 0.1667 (16.7%)
        BigDecimal ratio = new BigDecimal(response.emiEvaluation().rawNumbers().get("emiToSalaryRatio").toString());
        assertEquals(0, BigDecimal.valueOf(0.1900).compareTo(ratio));

        // 4. Confirm feasible is true (19.0% <= 30.0% limit)
        assertTrue(response.emiEvaluation().feasible());
    }
}
