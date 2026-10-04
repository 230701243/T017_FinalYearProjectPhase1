package com.financeapp.service;

import com.financeapp.dto.CategorizationResult;
import com.financeapp.dto.RecategorizeSummary;
import com.financeapp.ml.NaiveBayesClassifier;
import com.financeapp.model.Income;
import com.financeapp.model.Transaction;
import com.financeapp.repository.IncomeRepository;
import com.financeapp.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class CategoryService {

    public static final int MIN_TRAINING_EXAMPLES = 5;

    public static final Set<String> STANDARD_CATEGORIES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "Food & Dining",
            "Utilities & Bills",
            "Shopping & Tech",
            "Transportation",
            "Entertainment",
            "Subscriptions",
            "Healthcare",
            "Insurance",
            "Investments",
            "Income & Salary",
            "Freelance & Business",
            "Uncategorized"
    )));

    public static final Set<String> EXPENSE_CATEGORIES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "Food & Dining",
            "Utilities & Bills",
            "Shopping & Tech",
            "Transportation",
            "Entertainment",
            "Subscriptions",
            "Healthcare",
            "Insurance",
            "Investments",
            "Uncategorized"
    )));

    public static final Set<String> INCOME_CATEGORIES = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
            "Income & Salary",
            "Freelance & Business",
            "Investments",
            "Uncategorized"
    )));

    public static boolean isStandardCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return false;
        }
        for (String std : STANDARD_CATEGORIES) {
            if (std.equalsIgnoreCase(category.trim())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isStandardExpenseCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return false;
        }
        for (String std : EXPENSE_CATEGORIES) {
            if (std.equalsIgnoreCase(category.trim())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isStandardIncomeCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return false;
        }
        for (String std : INCOME_CATEGORIES) {
            if (std.equalsIgnoreCase(category.trim())) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeCategory(String category) {
        if (category == null || category.trim().isEmpty()) {
            return "Uncategorized";
        }
        for (String std : STANDARD_CATEGORIES) {
            if (std.equalsIgnoreCase(category.trim())) {
                return std;
            }
        }
        return "Uncategorized";
    }

    public static class CategoryRule {
        private final String category;
        private final List<String> keywords;

        public CategoryRule(String category, List<String> keywords) {
            this.category = category;
            this.keywords = Collections.unmodifiableList(keywords);
        }

        public String getCategory() { return category; }
        public List<String> getKeywords() { return keywords; }
    }

    private final List<CategoryRule> rules = new ArrayList<>();

    @Autowired(required = false)
    private TransactionRepository transactionRepository;

    @Autowired(required = false)
    private IncomeRepository incomeRepository;

    public CategoryService() {
        // Significantly expanded rule dictionary covering all standard categories, Indian merchants & financial descriptors

        // 1. Investments (covers investment, dividend, interest, savings, mutual funds, stocks, etc.)
        rules.add(new CategoryRule("Investments", Arrays.asList(
                "investment", "investments", "invest", "dividend", "interest", "stock", "stocks",
                "mutual fund", "sip", "shares", "equity", "etf", "crypto", "trading", "zerodha",
                "groww", "upstox", "savings", "saving", "deposit", "fd", "rd"
        )));

        // 2. Subscriptions (including streaming, gym, software, memberships)
        rules.add(new CategoryRule("Subscriptions", Arrays.asList(
                "subscription", "netflix", "spotify", "hotstar", "prime video", "prime", "disney",
                "youtube", "hulu", "apple music", "gym", "membership", "coursera", "icloud",
                "chatgpt", "patreon", "substack", "gaana", "wynk", "fitness"
        )));

        // 3. Entertainment (including movies, cinema, events, outings, friend expenses)
        rules.add(new CategoryRule("Entertainment", Arrays.asList(
                "movie", "cinema", "theatre", "pvr", "inox", "bookmyshow", "concert", "games",
                "gaming", "playstation", "steam", "event", "show", "bowling", "amusement", "fun",
                "cinepolis", "friend expenses", "friend", "friends", "outing", "party"
        )));

        // 4. Healthcare (including doctors, pharmacies, clinics, medical services)
        rules.add(new CategoryRule("Healthcare", Arrays.asList(
                "medical", "pharmacy", "doctor", "clinic", "hospital", "chemist", "medicine",
                "apollo", "1mg", "pharmeasy", "netmeds", "dental", "diagnostic", "lab", "care",
                "health", "dentist", "medplus",
                // Multilingual seed keywords: Hindi, Tamil, Malayalam, Kannada, Telugu, Bengali, Marathi
                "दवा", "दवाई", "अस्पताल", "इलाज", "डॉक्टर", "स्वास्थ्य",
                "மருந்து", "மருத்துவமனை", "மருத்துவர்", "சிகிச்சை",
                "മരുന്ന്", "ആശുപത്രി", "ഡോക്ടർ", "ചികിത്സ",
                "ಔಷಧ", "ಆಸ್ಪತ್ರೆ", "ವೈದ್ಯ", "ಚಿಕಿತ್ಸೆ",
                "మందులు", "ఆసుపత్రి", "వైద్యుడు", "చికిత్స",
                "ওষুধ", "হাসপাতাল", "ডাক্তার", "চিকিৎসা",
                "औषध", "दवाखाना", "रुग्णालय", "उपचार"
        )));

        // 5. Food & Dining
        rules.add(new CategoryRule("Food & Dining", Arrays.asList(
                "grocery", "supermarket", "food", "swiggy", "zomato", "dinner", "lunch", "breakfast",
                "mart", "restaurant", "cafe", "starbucks", "mcdonald", "kfc", "burger", "pizza",
                "domino", "blinkit", "zepto", "bigbasket", "instamart", "dunzo", "bakery", "subway",
                "eatfit", "kitchen", "sweet", "coffee", "snack",
                // Multilingual seed keywords: Hindi, Tamil, Malayalam, Kannada, Telugu, Bengali, Marathi
                "sabzi", "sabji", "सब्ज़ी", "सब्जी", "खाना", "राशन", "रोटी", "दूध", "फल", "चाय", "मिठाई",
                "காய்கறி", "உணவு", "சாப்பாடு", "மளிகை", "பால்", "பழம்", "தேநீர்",
                "പച്ചക്കറി", "ഭക്ഷണം", "പാല്", "അരി", "ചായ",
                "ತರಕಾರಿ", "ಊಟ", "ತಿಂಡಿ", "ಹಾಲು", "ಹಣ್ಣು", "ಕಾಫಿ",
                "కూరగాయలు", "ఆహారం", "భోజనం", "పాలు", "పండ్లు",
                "সবজি", "খাবার", "মাছ", "ভাত", "দুধ", "ফল", "চা",
                "भाजी", "जेवण", "दूध", "किराणा", "फळे", "चहा"
        )));

        // 6. Shopping & Tech
        rules.add(new CategoryRule("Shopping & Tech", Arrays.asList(
                "shopping", "amazon", "flipkart", "electronics", "cables", "tech", "gadget", "apple",
                "store", "laptop", "myntra", "ajio", "meesho", "clothing", "apparel", "shoes",
                "decathlon", "ikea", "mall", "retail", "fashion", "nykaa", "croma", "reliance digital",
                "purplle"
        )));

        // 7. Transportation
        rules.add(new CategoryRule("Transportation", Arrays.asList(
                "uber", "ola", "rapido", "fuel", "petrol", "diesel", "gas station", "flight", "train",
                "irctc", "cab", "taxi", "metro", "bus", "redbus", "fastag", "toll", "travel", "auto",
                "indigo", "air india", "spicejet", "parking",
                // Multilingual seed keywords: Hindi, Tamil, Malayalam, Kannada, Telugu, Bengali, Marathi
                "पेट्रोल", "डीज़ल", "किराया", "सवारी", "भाड़ा", "गाड़ी", "सफर",
                "பெட்ரோல்", "டீசல்", "பயணம்", "வண்டி", "பேருந்து", "ரயில்",
                "പെട്രോൾ", "ഡീസൽ", "യാത്ര", "വണ്ടി", "ബസ്",
                "ಪೆಟ್ರೋಲ್", "ಡೀಸೆಲ್", "ಪ್ರಯಾಣ", "ಬಸ್ಸು", "ರೈಲು",
                "పెట్రోల్", "డీజిల్", "ప్రయాణం", "బస్సు", "రైలు",
                "পেট্রোল", "ডিজেল", "ভাড়া", "গাড়ি", "বাস", "ট্রেন",
                "पेट्रोल", "डिझेल", "प्रवास", "भाडे", "गाडी"
        )));

        // 8. Utilities & Bills (including rent, EMI, utilities, phone/internet providers)
        rules.add(new CategoryRule("Utilities & Bills", Arrays.asList(
                "bill", "wifi", "electricity", "water", "utility", "utilities", "rent", "recharge",
                "mobile", "broadband", "internet", "dth", "bescom", "tatapower", "tneb", "lpg",
                "piped gas", "current", "eb bill", "maintenance", "jio", "airtel", "vi", "bsnl",
                "house", "housing", "cylinder", "emi", "loan",
                // Multilingual seed keywords: Hindi, Tamil, Malayalam, Kannada, Telugu, Bengali, Marathi
                "kiraya", "किराया", "बिजली", "पानी", "सिलेंडर", "गैस", "मकान", "ईएमआई", "कर्ज",
                "வாடகை", "மின்சாரம்", "தண்ணீர்", "சிலிண்டர்", "வீட்டு வாடகை", "கடன்",
                "വാടക", "വൈദ്യുതി", "വെള്ളം", "ഗ്യാസ്", "വാടകവീട്",
                "ಬಾಡಿಗೆ", "ವಿದ್ಯುತ್", "ನೀರು", "ಅನಿಲ", "ಸಾಲ",
                "అద్దె", "విద్యుత్", "నీరు", "గ్యాస్", "ఇంటి అద్దె", "రుణం",
                "ভাড়া", "বিদ্যুৎ", "জল", "গ্যাস", "বাড়ি ভাড়া",
                "भाडे", "वीज", "पाणी", "घरभाडे", "कर्ज"
        )));

        // 9. Insurance (including life, health, mediclaim, term, vehicle insurance & premiums)
        rules.add(new CategoryRule("Insurance", Arrays.asList(
                "insurance", "premium", "lic", "mediclaim", "health policy", "policy",
                "term insurance", "life insurance", "health insurance", "motor insurance",
                "car insurance", "bike insurance", "max bupa", "star health", "hdfc ergo",
                "icici lombard", "general insurance", "insure"
        )));

        // 10. Income & Salary (including salary, bonus, payout, refund, cashback, gift)
        rules.add(new CategoryRule("Income & Salary", Arrays.asList(
                "salary", "payroll", "bonus", "payout", "stipend", "wage", "refund",
                "cashback", "gift", "allowance"
        )));

        // 11. Freelance & Business
        rules.add(new CategoryRule("Freelance & Business", Arrays.asList(
                "freelance", "consulting", "contract", "invoice", "client", "upwork", "fiverr",
                "commission"
        )));
    }

    public List<CategoryRule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    public CategorizationResult categorize(String description) {
        return categorize(description, null);
    }

    public CategorizationResult categorize(String description, String username) {
        if (description == null || description.trim().isEmpty()) {
            return new CategorizationResult("Uncategorized", null, "UNASSIGNED");
        }

        String normalized = description.toLowerCase(Locale.ROOT).trim();

        // Step 1: Rule-based Keyword Dictionary Lookup
        for (CategoryRule rule : rules) {
            for (String keyword : rule.getKeywords()) {
                if (matchesKeyword(normalized, keyword)) {
                    return new CategorizationResult(
                            rule.getCategory(),
                            "Keyword: " + keyword,
                            "RULE"
                    );
                }
            }
        }

        // Step 2: ML Fallback Classifier (runs ONLY on Uncategorized items)
        if (username != null && transactionRepository != null && incomeRepository != null) {
            List<NaiveBayesClassifier.TrainingExample> trainingData = new ArrayList<>();

            List<Transaction> manualExpenses = transactionRepository.findByUsernameAndIsManuallyCategorizedTrue(username);
            for (Transaction t : manualExpenses) {
                // Only allow standard categories to train ML classifier; reject arbitrary text
                if (t.getCategory() != null && isStandardCategory(t.getCategory()) && !"Uncategorized".equalsIgnoreCase(t.getCategory())) {
                    trainingData.add(new NaiveBayesClassifier.TrainingExample(t.getDescription(), normalizeCategory(t.getCategory())));
                }
            }

            List<Income> manualIncomes = incomeRepository.findByUsernameAndIsManuallyCategorizedTrue(username);
            for (Income i : manualIncomes) {
                if (i.getCategory() != null && isStandardCategory(i.getCategory()) && !"Uncategorized".equalsIgnoreCase(i.getCategory())) {
                    trainingData.add(new NaiveBayesClassifier.TrainingExample(i.getDescription(), normalizeCategory(i.getCategory())));
                }
            }

            // Minimum Training Examples Threshold check (default: 5)
            if (trainingData.size() >= MIN_TRAINING_EXAMPLES) {
                NaiveBayesClassifier classifier = new NaiveBayesClassifier();
                classifier.train(trainingData);
                String predictedCategory = classifier.predict(description);

                if (predictedCategory != null && !predictedCategory.trim().isEmpty() && !"Uncategorized".equalsIgnoreCase(predictedCategory)) {
                    if (isStandardCategory(predictedCategory)) {
                        return new CategorizationResult(
                                normalizeCategory(predictedCategory),
                                null,
                                "ML"
                        );
                    }
                }
            }
        }

        // Fallback when no rule matched and ML threshold not met or prediction unassigned
        return new CategorizationResult("Uncategorized", null, "UNASSIGNED");
    }

    private boolean matchesKeyword(String text, String keyword) {
        if (keyword == null || keyword.isEmpty() || text == null || text.isEmpty()) return false;
        String k = keyword.toLowerCase(Locale.ROOT).trim();
        String t = text.toLowerCase(Locale.ROOT).trim();

        // Direct containment
        if (k.contains(" ") || k.contains("-")) {
            return t.contains(k);
        }

        // Unicode-aware word boundary pattern
        String regex = "(?i)(?U)(?:^|[^\\p{L}\\p{M}\\p{N}])" + Pattern.quote(k) + "(?:$|[^\\p{L}\\p{M}\\p{N}])";
        if (Pattern.compile(regex).matcher(t).find()) {
            return true;
        }
        return t.equals(k);
    }

    /**
     * Retroactively recategorizes all currently Uncategorized transactions for the given user.
     * Manual overrides are preserved.
     */
    public RecategorizeSummary recategorizeUncategorized(String username) {
        if (transactionRepository == null) {
            return new RecategorizeSummary(0, 0, 0, List.of(), List.of());
        }

        List<Transaction> transactions = transactionRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<RecategorizeSummary.RecategorizeDetail> matched = new ArrayList<>();
        List<RecategorizeSummary.RecategorizeDetail> unmatched = new ArrayList<>();

        int totalUncategorized = 0;

        for (Transaction t : transactions) {
            // Only process currently Uncategorized and not manually overridden
            if (!t.isManuallyCategorized() && (t.getCategory() == null || "Uncategorized".equalsIgnoreCase(t.getCategory()))) {
                totalUncategorized++;
                CategorizationResult result = categorize(t.getDescription(), username);

                if (result != null && !"Uncategorized".equalsIgnoreCase(result.category())) {
                    String prevCategory = t.getCategory();
                    t.setCategory(result.category());
                    t.setMatchedRule(result.matchedRule());
                    t.setConfidenceSource(result.confidenceSource());
                    transactionRepository.save(t);

                    matched.add(new RecategorizeSummary.RecategorizeDetail(
                            t.getId(),
                            t.getDescription(),
                            prevCategory,
                            result.category(),
                            result.matchedRule(),
                            result.confidenceSource()
                    ));
                } else {
                    unmatched.add(new RecategorizeSummary.RecategorizeDetail(
                            t.getId(),
                            t.getDescription(),
                            "Uncategorized",
                            "Uncategorized",
                            null,
                            "UNASSIGNED"
                    ));
                }
            }
        }

        // Also process Uncategorized Incomes for this user
        if (incomeRepository != null) {
            List<Income> incomes = incomeRepository.findByUsernameOrderByCreatedAtDesc(username);
            for (Income i : incomes) {
                if (!i.isManuallyCategorized() && (i.getCategory() == null || "Uncategorized".equalsIgnoreCase(i.getCategory()))) {
                    totalUncategorized++;
                    CategorizationResult result = categorize(i.getDescription(), username);
                    if (result != null && !"Uncategorized".equalsIgnoreCase(result.category())) {
                        String prevCategory = i.getCategory();
                        i.setCategory(result.category());
                        i.setMatchedRule(result.matchedRule());
                        i.setConfidenceSource(result.confidenceSource());
                        incomeRepository.save(i);

                        matched.add(new RecategorizeSummary.RecategorizeDetail(
                                i.getId(),
                                i.getDescription(),
                                prevCategory,
                                result.category(),
                                result.matchedRule(),
                                result.confidenceSource()
                        ));
                    } else {
                        unmatched.add(new RecategorizeSummary.RecategorizeDetail(
                                i.getId(),
                                i.getDescription(),
                                "Uncategorized",
                                "Uncategorized",
                                null,
                                "UNASSIGNED"
                        ));
                    }
                }
            }
        }

        return new RecategorizeSummary(totalUncategorized, matched.size(), unmatched.size(), matched, unmatched);
    }

    /**
     * Retroactively recategorizes all currently Uncategorized transactions across EVERY user.
     */
    public RecategorizeSummary recategorizeAllUsersUncategorized() {
        if (transactionRepository == null) {
            return new RecategorizeSummary(0, 0, 0, List.of(), List.of());
        }

        List<Transaction> transactions = transactionRepository.findAll();
        List<RecategorizeSummary.RecategorizeDetail> matched = new ArrayList<>();
        List<RecategorizeSummary.RecategorizeDetail> unmatched = new ArrayList<>();

        int totalUncategorized = 0;

        for (Transaction t : transactions) {
            if (!t.isManuallyCategorized() && (t.getCategory() == null || "Uncategorized".equalsIgnoreCase(t.getCategory()))) {
                totalUncategorized++;
                CategorizationResult result = categorize(t.getDescription(), t.getUsername());

                if (result != null && !"Uncategorized".equalsIgnoreCase(result.category())) {
                    String prevCategory = t.getCategory();
                    t.setCategory(result.category());
                    t.setMatchedRule(result.matchedRule());
                    t.setConfidenceSource(result.confidenceSource());
                    transactionRepository.save(t);

                    matched.add(new RecategorizeSummary.RecategorizeDetail(
                            t.getId(),
                            t.getDescription(),
                            prevCategory,
                            result.category(),
                            result.matchedRule(),
                            result.confidenceSource()
                    ));
                } else {
                    unmatched.add(new RecategorizeSummary.RecategorizeDetail(
                            t.getId(),
                            t.getDescription(),
                            "Uncategorized",
                            "Uncategorized",
                            null,
                            "UNASSIGNED"
                    ));
                }
            }
        }

        if (incomeRepository != null) {
            List<Income> incomes = incomeRepository.findAll();
            for (Income i : incomes) {
                if (!i.isManuallyCategorized() && (i.getCategory() == null || "Uncategorized".equalsIgnoreCase(i.getCategory()))) {
                    totalUncategorized++;
                    CategorizationResult result = categorize(i.getDescription(), i.getUsername());
                    if (result != null && !"Uncategorized".equalsIgnoreCase(result.category())) {
                        String prevCategory = i.getCategory();
                        i.setCategory(result.category());
                        i.setMatchedRule(result.matchedRule());
                        i.setConfidenceSource(result.confidenceSource());
                        incomeRepository.save(i);

                        matched.add(new RecategorizeSummary.RecategorizeDetail(
                                i.getId(),
                                i.getDescription(),
                                prevCategory,
                                result.category(),
                                result.matchedRule(),
                                result.confidenceSource()
                        ));
                    } else {
                        unmatched.add(new RecategorizeSummary.RecategorizeDetail(
                                i.getId(),
                                i.getDescription(),
                                "Uncategorized",
                                "Uncategorized",
                                null,
                                "UNASSIGNED"
                        ));
                    }
                }
            }
        }

        return new RecategorizeSummary(totalUncategorized, matched.size(), unmatched.size(), matched, unmatched);
    }

    /**
     * Sanitizes any non-standard categories (e.g. "family", "piggybank") in existing transactions.
     * If the transaction description matches a standard rule, it is categorized properly;
     * otherwise it defaults to "Uncategorized".
     */
    public List<Transaction> sanitizeNonStandardCategories(String username) {
        if (transactionRepository == null) {
            return List.of();
        }

        List<Transaction> transactions = transactionRepository.findByUsernameOrderByCreatedAtDesc(username);
        List<Transaction> updated = new ArrayList<>();

        for (Transaction t : transactions) {
            if (t.getCategory() != null && !isStandardCategory(t.getCategory())) {
                CategorizationResult result = categorize(t.getDescription(), username);
                if (!"Uncategorized".equalsIgnoreCase(result.category())) {
                    t.setCategory(result.category());
                    t.setMatchedRule(result.matchedRule());
                    t.setConfidenceSource(result.confidenceSource());
                } else {
                    t.setCategory("Uncategorized");
                    t.setMatchedRule(null);
                    t.setConfidenceSource("UNASSIGNED");
                }
                t.setManuallyCategorized(false);
                transactionRepository.save(t);
                updated.add(t);
            }
        }

        return updated;
    }
}
