package com.financeapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.financeapp.dto.DecisionAssistantRequest;
import com.financeapp.dto.DecisionAssistantResponse;
import com.financeapp.model.Income;
import com.financeapp.model.SavingsGoal;
import com.financeapp.model.Subscription;
import com.financeapp.model.Transaction;
import com.financeapp.model.User;
import com.financeapp.repository.CurrencyConfigRepository;
import com.financeapp.repository.IncomeRepository;
import com.financeapp.repository.SavingsGoalRepository;
import com.financeapp.repository.SubscriptionRepository;
import com.financeapp.repository.TransactionRepository;
import com.financeapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DecisionAssistantServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private IncomeRepository incomeRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private SavingsGoalRepository savingsGoalRepository;

    @Mock
    private LlmService llmService;

    @Mock
    private CurrencyConversionService currencyConversionService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CurrencyConfigRepository currencyConfigRepository;

    @Spy
    private RecommendationService recommendationService = new RecommendationService();

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private DecisionAssistantService decisionAssistantService;

    private String username = "testuser";

    @BeforeEach
    void setUp() {
        Income income1 = new Income(BigDecimal.valueOf(5000), "Monthly Salary", "Salary", username);
        income1.setCreatedAt(LocalDateTime.now());

        Transaction tx1 = new Transaction();
        tx1.setAmount(BigDecimal.valueOf(1500));
        tx1.setCategory("Housing");
        tx1.setDescription("Rent");
        tx1.setUsername(username);
        tx1.setCreatedAt(LocalDateTime.now());

        Transaction tx2 = new Transaction();
        tx2.setAmount(BigDecimal.valueOf(500));
        tx2.setCategory("Food & Dining");
        tx2.setDescription("Groceries");
        tx2.setUsername(username);
        tx2.setCreatedAt(LocalDateTime.now());

        Subscription sub = new Subscription("Netflix", BigDecimal.valueOf(20), "Entertainment", 5, username);
        sub.setActive(true);

        SavingsGoal goal = new SavingsGoal("Vacation", BigDecimal.valueOf(3000), BigDecimal.valueOf(1000), username);
        goal.setCreatedAt(LocalDateTime.now());

        when(incomeRepository.findByUsernameOrderByCreatedAtDesc(username)).thenReturn(List.of(income1));
        when(transactionRepository.findByUsernameOrderByCreatedAtDesc(username)).thenReturn(List.of(tx1, tx2));
        when(subscriptionRepository.findByUsernameAndIsActiveTrue(username)).thenReturn(List.of(sub));
        when(savingsGoalRepository.findByUsernameOrderByCreatedAtDesc(username)).thenReturn(List.of(goal));
    }

    @Test
    void testAssembleContextGeneralQuestion() {
        String expectedPrompt = DecisionAssistantService.buildSystemPrompt("USD", "$");
        when(llmService.generateCompletion(eq(expectedPrompt), anyString()))
                .thenReturn("Your financial situation is stable with a 60% savings ratio. This is not professional financial advice.");

        DecisionAssistantRequest request = new DecisionAssistantRequest();
        request.setQuestion("How is my financial health looking?");

        DecisionAssistantResponse response = decisionAssistantService.assembleContext(username, request);

        assertNotNull(response);
        assertEquals("How is my financial health looking?", response.question());
        assertNull(response.hypotheticalPurchase());
        assertNull(response.whatIfSimulation());
        assertNull(response.scenarioEvaluations(), "Scenarios should be null as scenario simulations are disconnected");

        // LLM Recommendation returned alongside raw snapshot
        assertEquals("Your financial situation is stable with a 60% savings ratio. This is not professional financial advice.", response.llmRecommendation());

        // Snapshot assertions
        assertNotNull(response.financialSnapshot());
        assertEquals(0, BigDecimal.valueOf(3000.00).compareTo(response.financialSnapshot().totalBalance()));
        assertEquals(0, BigDecimal.valueOf(5000.00).compareTo(response.financialSnapshot().monthlyIncome()));
        assertEquals(0, BigDecimal.valueOf(2000.00).compareTo(response.financialSnapshot().monthlyExpenses()));
        assertEquals(0, BigDecimal.valueOf(3000.00).compareTo(response.financialSnapshot().monthlySavings()));
        assertEquals(60.0, response.financialSnapshot().savingsRatio());

        // Goals
        assertEquals(1, response.activeSavingsGoals().size());
        assertEquals("Vacation", response.activeSavingsGoals().get(0).name());
        assertEquals(0, BigDecimal.valueOf(2000).compareTo(response.activeSavingsGoals().get(0).remainingAmount()));

        // Recent Transactions
        assertNotNull(response.recentTransactions());
        assertFalse(response.recentTransactions().isEmpty());
        assertEquals(3, response.recentTransactions().size()); // 2 expenses + 1 income

        // Verify LLM system prompt passed
        verify(llmService).generateCompletion(eq(expectedPrompt), anyString());
    }

    @Test
    void testAssembleContextWithHypotheticalPurchaseQuestion() {
        String expectedPrompt = DecisionAssistantService.buildSystemPrompt("USD", "$");
        when(llmService.generateCompletion(eq(expectedPrompt), anyString()))
                .thenReturn("Based on your ₹3,000 balance and ₹3,000 monthly savings, a ₹1,200 gaming PC is affordable. This is a general assessment, not a detailed scenario comparison.");

        DecisionAssistantRequest request = new DecisionAssistantRequest(
                "Can I afford a new gaming PC for ₹1,200?",
                new DecisionAssistantRequest.HypotheticalPurchase(
                        BigDecimal.valueOf(1200),
                        "Gaming PC",
                        "immediate"
                )
        );

        DecisionAssistantResponse response = decisionAssistantService.assembleContext(username, request);

        assertNotNull(response);
        assertEquals("Can I afford a new gaming PC for ₹1,200?", response.question());
        assertNotNull(response.hypotheticalPurchase());
        assertEquals(0, BigDecimal.valueOf(1200).compareTo(response.hypotheticalPurchase().getAmount()));
        assertEquals("Gaming PC", response.hypotheticalPurchase().getDescription());

        // Scenarios must be null (disconnected)
        assertNull(response.whatIfSimulation(), "What-if simulation should be disconnected");
        assertNull(response.scenarioEvaluations(), "Scenario evaluations should be disconnected");

        // Raw snapshot and recent transactions must be populated
        assertNotNull(response.financialSnapshot());
        assertNotNull(response.recentTransactions());
        assertEquals(3, response.recentTransactions().size());

        // LLM response returned
        assertEquals("Based on your ₹3,000 balance and ₹3,000 monthly savings, a ₹1,200 gaming PC is affordable. This is a general assessment, not a detailed scenario comparison.", response.llmRecommendation());

        verify(llmService).generateCompletion(eq(expectedPrompt), anyString());
    }

    @Test
    void testAssembleContextWithInrPreferredCurrency_PayloadConvertedAndSafetyNetApplied() throws Exception {
        User inrUser = new User(username, "inr@example.com", "secret", "IN", "INR");
        when(userRepository.findByUsername(username)).thenReturn(Optional.of(inrUser));

        BigDecimal inrRate = BigDecimal.valueOf(83.50);
        when(currencyConversionService.convertUsd(any(BigDecimal.class), eq("INR"))).thenAnswer(invocation -> {
            BigDecimal usd = invocation.getArgument(0);
            BigDecimal converted = usd.multiply(inrRate).setScale(2, RoundingMode.HALF_UP);
            return new CurrencyConversionService.ConvertedDisplay(usd, converted, "INR", "₹", "₹" + converted);
        });

        String inrPrompt = DecisionAssistantService.buildSystemPrompt("INR", "₹");
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        // Simulate LLM mistakenly responding with "$" despite system prompt
        when(llmService.generateCompletion(anyString(), anyString()))
                .thenReturn("Based on your current balance of $250,500.00 and monthly savings of $250,500.00, a $60,000.00 laptop is affordable. This is a general assessment.");

        DecisionAssistantRequest request = new DecisionAssistantRequest(
                "Can I afford a ₹60,000 laptop?",
                new DecisionAssistantRequest.HypotheticalPurchase(
                        BigDecimal.valueOf(718.56), // ~ ₹60,000 / 83.50
                        "Laptop",
                        "immediate"
                )
        );

        DecisionAssistantResponse response = decisionAssistantService.assembleContext(username, request);

        verify(llmService).generateCompletion(promptCaptor.capture(), payloadCaptor.capture());

        // 1. Verify prompt contains currency instructions
        String capturedPrompt = promptCaptor.getValue();
        assertEquals(inrPrompt, capturedPrompt);
        assertTrue(capturedPrompt.contains("All monetary values are in INR (₹)"));
        assertTrue(capturedPrompt.contains("Always write amounts using ₹"));
        assertTrue(capturedPrompt.contains("Never use $ or USD unless the currency is USD"));

        // 2. Verify payload has INR converted values and currency fields
        String capturedPayloadJson = payloadCaptor.getValue();
        JsonNode root = objectMapper.readTree(capturedPayloadJson);

        assertEquals("INR", root.path("currencyCode").asText());
        assertEquals("₹", root.path("currencySymbol").asText());
        assertEquals("Can I afford a ₹60,000 laptop?", root.path("userQuestion").asText());
        // 3000 * 83.50 = 250500.00
        assertEquals(250500.00, root.path("currentBalance").asDouble());
        // 5000 * 83.50 = 417500.00
        assertEquals(417500.00, root.path("monthlyIncome").asDouble());
        // 2000 * 83.50 = 167000.00
        assertEquals(167000.00, root.path("monthlyExpenses").asDouble());
        // 3000 * 83.50 = 250500.00
        assertEquals(250500.00, root.path("currentSavings").asDouble());

        // Goal: Vacation target 3000 * 83.50 = 250500.00
        JsonNode goalsNode = root.path("activeSavingsGoals");
        assertEquals(1, goalsNode.size());
        assertEquals(250500.00, goalsNode.get(0).path("targetAmount").asDouble());
        assertEquals(83500.00, goalsNode.get(0).path("currentAmount").asDouble());
        assertEquals(167000.00, goalsNode.get(0).path("remainingAmount").asDouble());

        // Purchase: 718.56 * 83.50 = 60000.00 (approx)
        JsonNode purchaseNode = root.path("hypotheticalPurchase");
        assertNotNull(purchaseNode);
        assertEquals(59999.76, purchaseNode.path("amount").asDouble(), 1.0);

        // 3. Verify safety net replaced all "$" with "₹"
        assertNotNull(response.llmRecommendation());
        assertFalse(response.llmRecommendation().contains("$"), "Response must not contain any '$' sign when user is INR");
        assertTrue(response.llmRecommendation().contains("₹250,500.00"));
        assertTrue(response.llmRecommendation().contains("₹60,000.00"));
    }

    @Test
    void testSafetyNetPreservesDollarWhenCurrencyIsUsd() {
        String usdPrompt = DecisionAssistantService.buildSystemPrompt("USD", "$");
        when(llmService.generateCompletion(eq(usdPrompt), anyString()))
                .thenReturn("Based on your $3,000.00 balance, the purchase is affordable.");

        DecisionAssistantRequest request = new DecisionAssistantRequest();
        request.setQuestion("Can I afford this?");

        DecisionAssistantResponse response = decisionAssistantService.assembleContext(username, request);

        assertNotNull(response.llmRecommendation());
        assertTrue(response.llmRecommendation().contains("$3,000.00"), "Response should retain '$' when currency is USD");
    }
}
