package com.financeapp.ml;

import java.util.*;

public class NaiveBayesClassifier {

    public record TrainingExample(String description, String category) {}

    private final Map<String, Integer> categoryDocCounts = new HashMap<>();
    private final Map<String, Map<String, Integer>> categoryWordCounts = new HashMap<>();
    private final Map<String, Integer> categoryTotalWords = new HashMap<>();
    private final Set<String> vocabulary = new HashSet<>();
    private int totalDocuments = 0;

    public void train(List<TrainingExample> examples) {
        categoryDocCounts.clear();
        categoryWordCounts.clear();
        categoryTotalWords.clear();
        vocabulary.clear();
        totalDocuments = 0;

        for (TrainingExample example : examples) {
            if (example.description() == null || example.category() == null) continue;
            String category = example.category().trim();
            if (category.isEmpty() || "Uncategorized".equalsIgnoreCase(category)) continue;

            totalDocuments++;
            categoryDocCounts.put(category, categoryDocCounts.getOrDefault(category, 0) + 1);

            List<String> tokens = tokenize(example.description());
            Map<String, Integer> wordCounts = categoryWordCounts.computeIfAbsent(category, k -> new HashMap<>());

            for (String token : tokens) {
                vocabulary.add(token);
                wordCounts.put(token, wordCounts.getOrDefault(token, 0) + 1);
                categoryTotalWords.put(category, categoryTotalWords.getOrDefault(category, 0) + 1);
            }
        }
    }

    public String predict(String description) {
        if (totalDocuments == 0 || categoryDocCounts.isEmpty()) {
            return null;
        }

        List<String> tokens = tokenize(description);
        if (tokens.isEmpty()) {
            return null;
        }

        String bestCategory = null;
        double maxLogProb = -Double.MAX_VALUE;
        int vocabSize = Math.max(1, vocabulary.size());

        for (String category : categoryDocCounts.keySet()) {
            // Prior probability: P(Category)
            double logProb = Math.log((double) categoryDocCounts.get(category) / totalDocuments);
            Map<String, Integer> wordCounts = categoryWordCounts.getOrDefault(category, Collections.emptyMap());
            int totalWords = categoryTotalWords.getOrDefault(category, 0);

            // Likelihood: P(W_1 | Category) * P(W_2 | Category) ...
            for (String token : tokens) {
                int count = wordCounts.getOrDefault(token, 0);
                // Laplace Smoothing (+1)
                double wordProb = (double) (count + 1) / (totalWords + vocabSize);
                logProb += Math.log(wordProb);
            }

            if (logProb > maxLogProb) {
                maxLogProb = logProb;
                bestCategory = category;
            }
        }

        return bestCategory;
    }

    private List<String> tokenize(String text) {
        if (text == null) return Collections.emptyList();
        String cleaned = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{M}\\p{N}\\s]", " ").trim();
        if (cleaned.isEmpty()) return Collections.emptyList();
        
        String[] parts = cleaned.split("\\s+");
        List<String> tokens = new ArrayList<>();
        for (String p : parts) {
            if (p.codePointCount(0, p.length()) >= 1) {
                tokens.add(p);
            }
        }
        return tokens;
    }
}
