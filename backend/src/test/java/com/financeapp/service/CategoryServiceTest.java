package com.financeapp.service;

import com.financeapp.dto.CategorizationResult;
import com.financeapp.dto.RecategorizeSummary;
import com.financeapp.model.Transaction;
import com.financeapp.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class CategoryServiceTest {

    private CategoryService categoryService;
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService();
        transactionRepository = Mockito.mock(TransactionRepository.class);
        ReflectionTestUtils.setField(categoryService, "transactionRepository", transactionRepository);
    }

    @Test
    @DisplayName("Keyword dictionary must contain standard categories and required keywords")
    void testKeywordDictionarySizeAndMerchants() {
        List<CategoryService.CategoryRule> rules = categoryService.getRules();
        assertNotNull(rules);

        int totalKeywords = rules.stream().mapToInt(r -> r.getKeywords().size()).sum();
        assertTrue(totalKeywords >= 50, "Expected at least 50 keywords, but found: " + totalKeywords);

        // Verify standard categories present
        Set<String> standardCategories = CategoryService.STANDARD_CATEGORIES;
        assertTrue(standardCategories.contains("Food & Dining"));
        assertTrue(standardCategories.contains("Utilities & Bills"));
        assertTrue(standardCategories.contains("Shopping & Tech"));
        assertTrue(standardCategories.contains("Transportation"));
        assertTrue(standardCategories.contains("Entertainment"));
        assertTrue(standardCategories.contains("Subscriptions"));
        assertTrue(standardCategories.contains("Healthcare"));
        assertTrue(standardCategories.contains("Investments"));
        assertTrue(standardCategories.contains("Income & Salary"));

        // Verify required merchant keywords exist
        List<String> allKeywords = rules.stream()
                .flatMap(r -> r.getKeywords().stream())
                .map(String::toLowerCase)
                .toList();

        List<String> required = Arrays.asList(
                "zomato", "swiggy", "bigbasket", "amazon", "flipkart",
                "uber", "ola", "jio", "airtel", "rent", "emi", "insurance",
                "medical", "pharmacy", "doctor", "movie", "netflix", "spotify", "gym",
                "bonus", "dividend", "interest", "refund", "cashback", "gift", "investment"
        );

        for (String keyword : required) {
            assertTrue(allKeywords.contains(keyword), "Missing required keyword: " + keyword);
        }
    }

    @Test
    @DisplayName("Test accurate categorization for common and Indian merchants and financial keywords")
    void testCategorizationMatches() {
        assertEquals("Food & Dining", categoryService.categorize("swiggy order delivery").category());
        assertEquals("Food & Dining", categoryService.categorize("Dinner at Zomato restaurant").category());
        assertEquals("Food & Dining", categoryService.categorize("BigBasket monthly grocery").category());
        assertEquals("Shopping & Tech", categoryService.categorize("Amazon electronics cable").category());
        assertEquals("Shopping & Tech", categoryService.categorize("Flipkart festival sale").category());
        assertEquals("Transportation", categoryService.categorize("Uber cab ride").category());
        assertEquals("Transportation", categoryService.categorize("Ola auto travel").category());
        assertEquals("Transportation", categoryService.categorize("travel").category());
        assertEquals("Utilities & Bills", categoryService.categorize("Jio prepaid recharge").category());
        assertEquals("Utilities & Bills", categoryService.categorize("Airtel fiber broadband").category());
        assertEquals("Utilities & Bills", categoryService.categorize("Home loan EMI payment").category());
        assertEquals("Insurance", categoryService.categorize("Life insurance premium").category());
        assertEquals("Utilities & Bills", categoryService.categorize("internet").category());
        assertEquals("Utilities & Bills", categoryService.categorize("current").category());
        assertEquals("Entertainment", categoryService.categorize("movie ticket").category());
        assertEquals("Entertainment", categoryService.categorize("friend expenses").category());
        assertEquals("Subscriptions", categoryService.categorize("Netflix monthly subscription").category());
        assertEquals("Subscriptions", categoryService.categorize("Spotify premium").category());
        assertEquals("Subscriptions", categoryService.categorize("Gold's gym membership").category());
        assertEquals("Healthcare", categoryService.categorize("Apollo pharmacy medicine").category());
        assertEquals("Healthcare", categoryService.categorize("Doctor consultation fee").category());
        assertEquals("Healthcare", categoryService.categorize("medical tests").category());
        assertEquals("Investments", categoryService.categorize("investment").category());
        assertEquals("Investments", categoryService.categorize("Stock market investment").category());
        assertEquals("Investments", categoryService.categorize("mutual fund dividend payout").category());
        assertEquals("Investments", categoryService.categorize("fixed deposit interest").category());
        assertEquals("Investments", categoryService.categorize("savings").category());
        assertEquals("Income & Salary", categoryService.categorize("annual performance bonus").category());
        assertEquals("Income & Salary", categoryService.categorize("cashback reward").category());
    }

    @Test
    @DisplayName("Investment transaction #7 matches rule and is not UNASSIGNED")
    void testInvestmentTransactionCategorization() {
        CategorizationResult result = categoryService.categorize("investment");
        assertEquals("Investments", result.category());
        assertEquals("Keyword: investment", result.matchedRule());
        assertEquals("RULE", result.confidenceSource());
        assertNotEquals("UNASSIGNED", result.confidenceSource());
    }

    @Test
    @DisplayName("Test word boundary prevents false substring matches")
    void testWordBoundaryPrecision() {
        CategorizationResult result = categoryService.categorize("movie");
        assertEquals("Entertainment", result.category());
        assertNotEquals("Utilities & Bills", result.category());

        CategorizationResult viResult = categoryService.categorize("vi recharge");
        assertEquals("Utilities & Bills", viResult.category());
    }

    @Test
    @DisplayName("Standard category validation validates Investments and rejects non-standard names")
    void testStandardCategoryValidation() {
        assertFalse(CategoryService.isStandardCategory("family"));
        assertFalse(CategoryService.isStandardCategory("piggybank"));

        assertTrue(CategoryService.isStandardCategory("Investments"));
        assertTrue(CategoryService.isStandardCategory("investments"));
        assertTrue(CategoryService.isStandardCategory("Food & Dining"));
        assertTrue(CategoryService.isStandardCategory("Utilities & Bills"));
    }

    @Test
    @DisplayName("Retroactive recategorization processes all uncategorized items successfully")
    void testRetroactiveRecategorization() {
        Transaction tx1 = new Transaction(BigDecimal.valueOf(119.76), "investment", "Cash", "testuser");
        tx1.setId(7L);
        tx1.setCategory("Uncategorized");

        Transaction tx2 = new Transaction(BigDecimal.valueOf(107.78), "savings", "Cash", "testuser");
        tx2.setId(8L);
        tx2.setCategory("Uncategorized");

        Transaction tx3 = new Transaction(BigDecimal.valueOf(59.88), "friend expenses", "Cash", "testuser");
        tx3.setId(11L);
        tx3.setCategory("Uncategorized");

        List<Transaction> list = Arrays.asList(tx1, tx2, tx3);
        when(transactionRepository.findByUsernameOrderByCreatedAtDesc("testuser")).thenReturn(list);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RecategorizeSummary summary = categoryService.recategorizeUncategorized("testuser");

        assertEquals(3, summary.totalUncategorizedProcessed());
        assertEquals(3, summary.matchedCount());
        assertEquals(0, summary.unmatchedCount());

        assertEquals("Investments", tx1.getCategory());
        assertEquals("Investments", tx2.getCategory());
        assertEquals("Entertainment", tx3.getCategory());
    }
}
