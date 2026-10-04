package com.financeapp.controller;

import com.financeapp.dto.DecisionAssistantRequest;
import com.financeapp.dto.DecisionAssistantResponse;
import com.financeapp.service.DecisionAssistantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class DecisionAssistantController {

    @Autowired
    private DecisionAssistantService decisionAssistantService;

    @PostMapping("/decision-assistant")
    public ResponseEntity<DecisionAssistantResponse> handleDecisionAssistant(
            @RequestBody(required = false) DecisionAssistantRequest request,
            Authentication authentication
    ) {
        String username = authentication.getName();
        DecisionAssistantResponse response = decisionAssistantService.assembleContext(
                username,
                request != null ? request : new DecisionAssistantRequest()
        );
        return ResponseEntity.ok(response);
    }
}
