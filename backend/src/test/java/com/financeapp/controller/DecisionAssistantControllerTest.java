package com.financeapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.financeapp.dto.DecisionAssistantRequest;
import com.financeapp.dto.DecisionAssistantResponse;
import com.financeapp.dto.DecisionScenarioResult;
import com.financeapp.dto.RiskScoreResponse;
import com.financeapp.service.DecisionAssistantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
public class DecisionAssistantControllerTest {

    private MockMvc mockMvc;

    private ObjectMapper objectMapper;

    @Mock
    private DecisionAssistantService decisionAssistantService;

    @InjectMocks
    private DecisionAssistantController decisionAssistantController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(decisionAssistantController).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void testPostDecisionAssistantEndpoint() throws Exception {
        DecisionAssistantRequest.HypotheticalPurchase purchase = new DecisionAssistantRequest.HypotheticalPurchase(
                BigDecimal.valueOf(1500), "New Laptop", "immediate"
        );
        DecisionAssistantRequest request = new DecisionAssistantRequest("Can I afford this laptop?", purchase);

        DecisionScenarioResult buyNowMock = new DecisionScenarioResult(
                true, BigDecimal.valueOf(3000), Collections.emptyList(), Map.of("currentSavings", 4500, "purchaseCost", 1500)
        );
        DecisionScenarioResult waitMock = new DecisionScenarioResult(
                true, BigDecimal.valueOf(3000), Collections.emptyList(), Map.of("monthsToWait", 0)
        );
        DecisionScenarioResult emiMock = new DecisionScenarioResult(
                true, BigDecimal.valueOf(4250), Collections.emptyList(), Map.of("emiAmount", 250)
        );

        DecisionAssistantResponse mockResponse = new DecisionAssistantResponse(
                "Can I afford this laptop?",
                purchase,
                new DecisionAssistantResponse.FinancialSnapshot(
                        BigDecimal.valueOf(4500),
                        BigDecimal.valueOf(6000),
                        BigDecimal.valueOf(1500),
                        BigDecimal.valueOf(6000),
                        BigDecimal.valueOf(1500),
                        BigDecimal.valueOf(4500),
                        75.0,
                        BigDecimal.valueOf(50),
                        Collections.emptyList()
                ),
                null,
                Collections.emptyList(),
                List.of(new DecisionAssistantResponse.RecentTransactionSummary(1L, "EXPENSE", "Groceries", BigDecimal.valueOf(50), "Food", "2026-09-04T12:00:00")),
                null,
                null,
                null,
                "Based on your ₹4,500 balance and ₹4,500 monthly savings, this laptop is affordable. This is a general assessment, not a detailed scenario comparison.",
                LocalDateTime.now()
        );

        Authentication auth = new UsernamePasswordAuthenticationToken("demoUser", "password");

        when(decisionAssistantService.assembleContext(eq("demoUser"), any(DecisionAssistantRequest.class)))
                .thenReturn(mockResponse);

        mockMvc.perform(post("/api/decision-assistant")
                        .principal(auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("Can I afford this laptop?"))
                .andExpect(jsonPath("$.hypotheticalPurchase.amount").value(1500))
                .andExpect(jsonPath("$.hypotheticalPurchase.description").value("New Laptop"))
                .andExpect(jsonPath("$.financialSnapshot.totalBalance").value(4500))
                .andExpect(jsonPath("$.financialSnapshot.monthlyIncome").value(6000))
                .andExpect(jsonPath("$.financialSnapshot.monthlyExpenses").value(1500))
                .andExpect(jsonPath("$.recentTransactions[0].description").value("Groceries"))
                .andExpect(jsonPath("$.llmRecommendation").value("Based on your ₹4,500 balance and ₹4,500 monthly savings, this laptop is affordable. This is a general assessment, not a detailed scenario comparison."));
    }
}
