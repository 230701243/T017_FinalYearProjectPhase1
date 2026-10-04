package com.financeapp.repository;

import com.financeapp.model.CurrencyConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurrencyConfigRepository extends JpaRepository<CurrencyConfig, Long> {

    Optional<CurrencyConfig> findByCurrencyCodeIgnoreCase(String currencyCode);

    Optional<CurrencyConfig> findByCountryCodeIgnoreCase(String countryCode);

    boolean existsByCurrencyCodeIgnoreCase(String currencyCode);

    List<CurrencyConfig> findAllByOrderByCurrencyCodeAsc();
}
