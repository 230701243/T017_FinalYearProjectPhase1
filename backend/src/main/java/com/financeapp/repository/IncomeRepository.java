package com.financeapp.repository;

import com.financeapp.model.Income;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncomeRepository extends JpaRepository<Income, Long> {
    List<Income> findByUsernameOrderByCreatedAtDesc(String username);
    List<Income> findByUsernameAndIsManuallyCategorizedTrue(String username);
}
