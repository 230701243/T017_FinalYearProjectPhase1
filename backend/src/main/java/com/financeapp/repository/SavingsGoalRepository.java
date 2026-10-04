package com.financeapp.repository;

import com.financeapp.model.SavingsGoal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, Long> {
    List<SavingsGoal> findByUsernameOrderByCreatedAtDesc(String username);
}
