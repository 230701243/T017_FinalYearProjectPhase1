package com.financeapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.financeapp.repository")
@EnableMongoRepositories(basePackages = "com.financeapp.repository")
public class FinanceAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(FinanceAppApplication.class, args);
    }
}
