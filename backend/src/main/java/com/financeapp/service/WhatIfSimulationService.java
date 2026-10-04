package com.financeapp.service;

import com.financeapp.dto.MultiScenarioWhatIfResponse;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service to store and retrieve active/most recent what-if simulations per user.
 */
@Service
public class WhatIfSimulationService {

    private final Map<String, MultiScenarioWhatIfResponse> activeSimulations = new ConcurrentHashMap<>();

    public void setActiveSimulation(String username, MultiScenarioWhatIfResponse response) {
        if (username != null && response != null) {
            activeSimulations.put(username, response);
        }
    }

    public MultiScenarioWhatIfResponse getActiveSimulation(String username) {
        return username != null ? activeSimulations.get(username) : null;
    }

    public boolean hasActiveSimulation(String username) {
        return username != null && activeSimulations.containsKey(username);
    }

    public void clearActiveSimulation(String username) {
        if (username != null) {
            activeSimulations.remove(username);
        }
    }
}
