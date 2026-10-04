package com.financeapp.repository;

import com.financeapp.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUsernameOrderByCreatedAtDesc(String username);
    List<Subscription> findByUsernameAndIsActiveTrue(String username);
}
