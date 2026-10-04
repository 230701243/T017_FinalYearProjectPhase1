package com.financeapp.controller;

import com.financeapp.dto.SubscriptionRequest;
import com.financeapp.model.Subscription;
import com.financeapp.repository.SubscriptionRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @PostMapping
    public ResponseEntity<Subscription> createSubscription(
            @Valid @RequestBody SubscriptionRequest request,
            Authentication authentication
    ) {
        Subscription subscription = new Subscription();
        subscription.setName(request.getName());
        subscription.setAmount(request.getAmount());
        subscription.setCategory(request.getCategory());
        subscription.setDueDay(request.getDueDay());
        if (request.getBillingCycle() != null) {
            subscription.setBillingCycle(request.getBillingCycle());
        }
        subscription.setActive(request.isActive());
        subscription.setUsername(authentication.getName());

        Subscription saved = subscriptionRepository.save(subscription);
        return ResponseEntity.ok(saved);
    }

    @GetMapping
    public ResponseEntity<List<Subscription>> getSubscriptions(Authentication authentication) {
        List<Subscription> subscriptions = subscriptionRepository
                .findByUsernameOrderByCreatedAtDesc(authentication.getName());
        return ResponseEntity.ok(subscriptions);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Subscription> updateSubscription(
            @PathVariable Long id,
            @Valid @RequestBody SubscriptionRequest request,
            Authentication authentication
    ) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));

        if (!subscription.getUsername().equals(authentication.getName())) {
            return ResponseEntity.status(403).build();
        }

        subscription.setName(request.getName());
        subscription.setAmount(request.getAmount());
        subscription.setCategory(request.getCategory());
        subscription.setDueDay(request.getDueDay());
        if (request.getBillingCycle() != null) {
            subscription.setBillingCycle(request.getBillingCycle());
        }
        subscription.setActive(request.isActive());

        Subscription updated = subscriptionRepository.save(subscription);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSubscription(
            @PathVariable Long id,
            Authentication authentication
    ) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Subscription not found"));

        if (!subscription.getUsername().equals(authentication.getName())) {
            return ResponseEntity.status(403).build();
        }

        subscriptionRepository.delete(subscription);
        return ResponseEntity.noContent().build();
    }
}
