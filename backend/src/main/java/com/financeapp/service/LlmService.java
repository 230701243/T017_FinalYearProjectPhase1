package com.financeapp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${llm.api.key:${LLM_API_KEY:${OPENAI_API_KEY:}}}")
    private String apiKey;

    @Value("${llm.api.url:${LLM_API_URL:https://api.openai.com/v1/chat/completions}}")
    private String apiUrl;

    @Value("${llm.api.model:${LLM_MODEL:gpt-4o-mini}}")
    private String model;

    @Value("${llm.api.timeout-seconds:10}")
    private int timeoutSeconds;

    public LlmService() {
        this(new RestTemplateBuilder()
                .setConnectTimeout(Duration.ofSeconds(45))
                .setReadTimeout(Duration.ofSeconds(45))
                .build(), new ObjectMapper());
    }

    public LlmService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    // Setters for testability / configuration
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Calls the chat completion endpoint with a system prompt and JSON payload.
     * Guaranteed never to throw or crash; returns structured fallback text on any error, timeout, or missing key.
     *
     * @param systemPrompt System instructions for the LLM
     * @param jsonPayload  Pre-computed financial state and scenario JSON
     * @return Model response text or clear fallback message
     */
    public String generateCompletion(String systemPrompt, String jsonPayload) {
        // 1. Guard against missing or placeholder API key
        if (apiKey == null || apiKey.trim().isEmpty() || apiKey.equalsIgnoreCase("your_llm_api_key_here") || apiKey.startsWith("your_")) {
            log.warn("LLM API call skipped: Missing or placeholder API key.");
            return buildFallbackMessage("LLM API key is not configured or missing in environment.");
        }

        try {
            String trimmedKey = apiKey.trim();
            String effectiveUrl = (apiUrl != null && !apiUrl.trim().isEmpty()) ? apiUrl.trim() : "https://api.openai.com/v1/chat/completions";
            String effectiveModel = (model != null && !model.trim().isEmpty()) ? model.trim() : "gpt-4o-mini";

            // Automatically detect NVIDIA NIM API keys and adjust endpoint / model if defaulting to OpenAI
            if (trimmedKey.startsWith("nvapi-")) {
                if (effectiveUrl.contains("api.openai.com")) {
                    effectiveUrl = "https://integrate.api.nvidia.com/v1/chat/completions";
                }
                if ("gpt-4o-mini".equalsIgnoreCase(effectiveModel)) {
                    effectiveModel = "meta/llama-3.2-11b-vision-instruct";
                }
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(trimmedKey);

            Map<String, Object> requestBody = Map.of(
                    "model", effectiveModel,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt != null ? systemPrompt : "You are a financial advisor."),
                            Map.of("role", "user", "content", jsonPayload != null ? jsonPayload : "{}")
                    ),
                    "temperature", 0.3,
                    "max_tokens", 450
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    effectiveUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode rootNode = objectMapper.readTree(response.getBody());
                JsonNode choices = rootNode.path("choices");
                if (choices.isArray() && !choices.isEmpty()) {
                    JsonNode messageNode = choices.get(0).path("message");
                    String content = messageNode.path("content").asText();
                    if (content != null && !content.trim().isEmpty()) {
                        return content.trim();
                    }
                }
            }

            log.warn("LLM API response was empty or unexpected format. Response status: {}", response.getStatusCode());
            return buildFallbackMessage("Received an empty or unparseable response from the provider.");

        } catch (ResourceAccessException ex) {
            log.error("LLM API request timed out or network error after {}s: {}", timeoutSeconds, ex.getMessage());
            return buildFallbackMessage("Request timed out or failed to reach the AI provider within " + timeoutSeconds + " seconds.");
        } catch (RestClientResponseException ex) {
            log.error("LLM API returned HTTP error {}: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return buildFallbackMessage("Provider returned HTTP status " + ex.getStatusCode().value() + " (" + ex.getStatusText() + ").");
        } catch (Exception ex) {
            log.error("Unexpected error during LLM API call: {}", ex.getMessage(), ex);
            return buildFallbackMessage("An unexpected error occurred: " + ex.getMessage());
        }
    }

    private String buildFallbackMessage(String reason) {
        return String.format(
                "⚠️ [AI Assistant Offline Fallback] %s\n\n" +
                "Please rely on the pre-computed deterministic scenario evaluations (Buy Now, Wait Option, and EMI Option) and live risk score shown in your report above.",
                reason
        );
    }
}
