package com.financeapp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class LlmServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private LlmService llmService;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        llmService = new LlmService(restTemplate, objectMapper);
        llmService.setApiUrl("https://api.openai.com/v1/chat/completions");
        llmService.setModel("gpt-4o-mini");
        llmService.setTimeoutSeconds(10);
    }

    @Test
    @DisplayName("Fallback path: Returns clear fallback when API key is null or empty")
    void testMissingApiKey_ReturnsFallback() {
        llmService.setApiKey(null);

        String result = llmService.generateCompletion("System prompt", "{\"data\": 123}");

        assertNotNull(result);
        assertTrue(result.contains("[AI Assistant Offline Fallback]"));
        assertTrue(result.contains("LLM API key is not configured"));
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Fallback path: Returns clear fallback when API key is default placeholder")
    void testPlaceholderApiKey_ReturnsFallback() {
        llmService.setApiKey("your_llm_api_key_here");

        String result = llmService.generateCompletion("System prompt", "{\"data\": 123}");

        assertNotNull(result);
        assertTrue(result.contains("[AI Assistant Offline Fallback]"));
        assertTrue(result.contains("LLM API key is not configured"));
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Fallback path: Returns clear fallback when API call times out")
    void testTimeout_ReturnsFallback() {
        llmService.setApiKey("valid-test-key");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("Read timed out after 10000ms"));

        String result = llmService.generateCompletion("System prompt", "{\"data\": 123}");

        assertNotNull(result);
        assertTrue(result.contains("[AI Assistant Offline Fallback]"));
        assertTrue(result.contains("timed out or failed to reach the AI provider"));
    }

    @Test
    @DisplayName("Fallback path: Returns clear fallback on HTTP 401 Unauthorized or 500 Error")
    void testHttpError_ReturnsFallback() {
        llmService.setApiKey("invalid-key");

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "Unauthorized"));

        String result = llmService.generateCompletion("System prompt", "{\"data\": 123}");

        assertNotNull(result);
        assertTrue(result.contains("[AI Assistant Offline Fallback]"));
        assertTrue(result.contains("401"));
    }

    @Test
    @DisplayName("Success path: Parses and returns text content on valid 200 response")
    void testSuccess_ReturnsResponseContent() {
        llmService.setApiKey("valid-test-key");

        String mockResponseBody = """
                {
                  "id": "chatcmpl-123",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "Based on your pre-computed financials, buying via EMI is your safest strategy."
                      },
                      "finish_reason": "stop"
                    }
                  ]
                }
                """;

        when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(mockResponseBody, HttpStatus.OK));

        String result = llmService.generateCompletion("System prompt", "{\"data\": 123}");

        assertNotNull(result);
        assertEquals("Based on your pre-computed financials, buying via EMI is your safest strategy.", result);
    }

    @Test
    @DisplayName("NVIDIA NIM path: Automatically routes nvapi key to NVIDIA endpoint and parses response")
    void testNvidiaNimKey_AutoRoutesAndParsesResponse() {
        llmService.setApiKey("nvapi-test-key-12345");
        llmService.setApiUrl("https://api.openai.com/v1/chat/completions");
        llmService.setModel("gpt-4o-mini");

        String mockResponseBody = """
                {
                  "id": "chatcmpl-nv-123",
                  "choices": [
                    {
                      "index": 0,
                      "message": {
                        "role": "assistant",
                        "content": "AI Financial advice generated by Llama on NVIDIA NIM."
                      },
                      "finish_reason": "stop"
                    }
                  ]
                }
                """;

        when(restTemplate.exchange(
                eq("https://integrate.api.nvidia.com/v1/chat/completions"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(new ResponseEntity<>(mockResponseBody, HttpStatus.OK));

        String result = llmService.generateCompletion("System prompt", "{\"data\": 123}");

        assertNotNull(result);
        assertEquals("AI Financial advice generated by Llama on NVIDIA NIM.", result);
        verify(restTemplate).exchange(
                eq("https://integrate.api.nvidia.com/v1/chat/completions"),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        );
    }
}
