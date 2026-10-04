package com.financeapp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.financeapp.dto.DecisionAssistantRequest;
import com.financeapp.dto.DecisionAssistantResponse;
import com.financeapp.model.Income;
import com.financeapp.model.Transaction;
import com.financeapp.model.User;
import com.financeapp.repository.IncomeRepository;
import com.financeapp.repository.TransactionRepository;
import com.financeapp.repository.UserRepository;
import com.financeapp.service.DecisionAssistantService;
import com.financeapp.service.LlmService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class DecisionAssistantFullFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IncomeRepository incomeRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    @MockBean
    private LlmService llmService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(llmService);
    }

    @Test
    @WithMockUser(username = "flowUser")
    @DisplayName("Full Flow: Ask 'Can I afford a ₹60,000 laptop?' -> Raw snapshot + general LLM assessment, no scenario comparisons")
    void testFullFlow_AffordabilityGeneralAssessment() throws Exception {
        String username = "flowUser";

        // Setup: Current Savings = $80,000 (Income: $90,000, Expense: $10,000)
        Income income = new Income(BigDecimal.valueOf(90000), "Salary", "Salary", username);
        income.setCategory("Income & Salary");
        income.setConfidenceSource("MANUAL");
        income.setCreatedAt(LocalDateTime.now());
        incomeRepository.save(income);

        Transaction expense = new Transaction();
        expense.setAmount(BigDecimal.valueOf(10000));
        expense.setCategory("Utilities & Bills");
        expense.setDescription("Living Expenses");
        expense.setUsername(username);
        expense.setPaymentMethod("Debit");
        expense.setConfidenceSource("MANUAL");
        expense.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(expense);

        // General assessment LLM response without Buy Now / Wait / EMI scenario breakdowns
        String expectedLlmResponse = "Based on your current balance of $80,000 and strong monthly net income of $80,000, a laptop is comfortably affordable with your existing cash reserves. Please note this is a general financial assessment based on your current snapshot, not a detailed scenario comparison. This is not professional financial advice.";
        when(llmService.generateCompletion(anyString(), anyString()))
                .thenReturn(expectedLlmResponse);

        DecisionAssistantRequest request = new DecisionAssistantRequest(
                "Can I afford a laptop?",
                null
        );

        MvcResult mvcResult = mockMvc.perform(post("/api/decision-assistant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String responseJson = mvcResult.getResponse().getContentAsString();
        DecisionAssistantResponse response = objectMapper.readValue(responseJson, DecisionAssistantResponse.class);

        // 1. Confirm Scenarios are null (disconnected)
        assertNull(response.scenarioEvaluations(), "Scenario evaluations must be disconnected/null");
        assertNull(response.whatIfSimulation(), "What-if simulation must be disconnected/null");

        // 2. Confirm Raw Financial Snapshot & Recent Transactions are returned
        assertNotNull(response.financialSnapshot());
        assertEquals(0, BigDecimal.valueOf(80000.00).compareTo(response.financialSnapshot().totalBalance()));
        assertEquals(0, BigDecimal.valueOf(90000.00).compareTo(response.financialSnapshot().monthlyIncome()));
        assertEquals(0, BigDecimal.valueOf(10000.00).compareTo(response.financialSnapshot().monthlyExpenses()));
        assertNotNull(response.recentTransactions());
        assertFalse(response.recentTransactions().isEmpty());

        // 3. Confirm LLM Response
        assertNotNull(response.llmRecommendation());
        String llmText = response.llmRecommendation().toLowerCase();
        assertFalse(llmText.contains("buy now option") || llmText.contains("option a:") || llmText.contains("option b:") || llmText.contains("emi option:"),
                "Response must not include Buy Now / Wait / EMI scenario breakdowns");
        assertTrue(llmText.contains("general") && (llmText.contains("assessment") || llmText.contains("snapshot")),
                "Response must note that it is a general assessment");
    }

    @Test
    @WithMockUser(username = "inrFlowUser")
    @DisplayName("Full Flow: INR preferred currency -> LLM payload in INR, system prompt with INR, response in ₹ with no $")
    void testFullFlow_InrPreferredCurrencyAffordability() throws Exception {
        String username = "inrFlowUser";

        if (userRepository.findByUsername(username).isEmpty()) {
            User inrUser = new User(username, "inrflow@test.com", "secret", "IN", "INR");
            userRepository.save(inrUser);
        }

        // Setup: Current Balance = $1,000 USD (Income: $1,200, Expense: $200)
        // Rate for INR = 83.50 -> Balance = ₹83,500.00, Income = ₹100,200.00, Expense = ₹16,700.00
        Income income = new Income(BigDecimal.valueOf(1200), "Salary", "Salary", username);
        income.setCategory("Income & Salary");
        income.setConfidenceSource("MANUAL");
        income.setCreatedAt(LocalDateTime.now());
        incomeRepository.save(income);

        Transaction expense = new Transaction();
        expense.setAmount(BigDecimal.valueOf(200));
        expense.setCategory("Food & Dining");
        expense.setDescription("Monthly food");
        expense.setUsername(username);
        expense.setPaymentMethod("UPI");
        expense.setConfidenceSource("MANUAL");
        expense.setCreatedAt(LocalDateTime.now());
        transactionRepository.save(expense);

        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        // Simulate LLM response that mistakenly includes "$"
        when(llmService.generateCompletion(anyString(), anyString()))
                .thenReturn("Based on your balance of $83,500.00 and monthly savings of $83,500.00, a ₹60,000 laptop is affordable. This is a general assessment.");

        DecisionAssistantRequest request = new DecisionAssistantRequest(
                "Can I afford a ₹60,000 laptop?",
                null
        );

        MvcResult mvcResult = mockMvc.perform(post("/api/decision-assistant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        verify(llmService).generateCompletion(promptCaptor.capture(), payloadCaptor.capture());

        // Verify system prompt
        String prompt = promptCaptor.getValue();
        assertTrue(prompt.contains("All monetary values are in INR (₹)"));
        assertTrue(prompt.contains("Always write amounts using ₹"));
        assertTrue(prompt.contains("Never use $ or USD unless the currency is USD"));

        // Verify payload JSON
        String payload = payloadCaptor.getValue();
        assertTrue(payload.contains("\"currencyCode\":\"INR\""));
        assertTrue(payload.contains("\"currencySymbol\":\"₹\""));
        assertTrue(payload.contains("\"currentBalance\":83500.00"));
        assertTrue(payload.contains("\"monthlyIncome\":100200.00"));
        assertTrue(payload.contains("\"monthlyExpenses\":16700.00"));

        // Verify response
        String responseJson = mvcResult.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        DecisionAssistantResponse response = objectMapper.readValue(responseJson, DecisionAssistantResponse.class);

        assertNotNull(response.llmRecommendation());
        assertFalse(response.llmRecommendation().contains("$"), "Response must not contain any '$' sign");
        assertTrue(response.llmRecommendation().contains("₹83,500.00"));
        assertTrue(response.llmRecommendation().contains("₹60,000"));
    }
}
