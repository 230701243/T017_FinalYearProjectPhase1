package com.financeapp.controller;

import com.financeapp.dto.CurrencyConfigDto;
import com.financeapp.dto.CurrencyUpdateRequest;
import com.financeapp.service.CurrencyConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/currencies")
@CrossOrigin(origins = "*")
public class CurrencyConfigController {

    private final CurrencyConfigService currencyConfigService;

    public CurrencyConfigController(CurrencyConfigService currencyConfigService) {
        this.currencyConfigService = currencyConfigService;
    }

    @GetMapping
    public ResponseEntity<List<CurrencyConfigDto>> getAllCurrencies() {
        return ResponseEntity.ok(currencyConfigService.getAllCurrencies());
    }

    @GetMapping("/{currencyCode}")
    public ResponseEntity<CurrencyConfigDto> getCurrencyByCode(@PathVariable String currencyCode) {
        return ResponseEntity.ok(currencyConfigService.getCurrencyByCode(currencyCode));
    }

    @GetMapping("/country/{countryCode}")
    public ResponseEntity<CurrencyConfigDto> getCurrencyByCountry(@PathVariable String countryCode) {
        return ResponseEntity.ok(currencyConfigService.getCurrencyByCountry(countryCode));
    }

    @PutMapping("/{currencyCode}")
    public ResponseEntity<CurrencyConfigDto> updateCurrency(
            @PathVariable String currencyCode,
            @RequestBody CurrencyUpdateRequest request) {
        return ResponseEntity.ok(currencyConfigService.updateCurrency(currencyCode, request));
    }

    @GetMapping("/convert")
    public ResponseEntity<Map<String, Object>> convert(
            @RequestParam BigDecimal amount,
            @RequestParam String from,
            @RequestParam String to) {
        BigDecimal convertedAmount = currencyConfigService.convert(amount, from, to);
        return ResponseEntity.ok(Map.of(
                "amount", amount,
                "from", from.toUpperCase(),
                "to", to.toUpperCase(),
                "convertedAmount", convertedAmount
        ));
    }
}
