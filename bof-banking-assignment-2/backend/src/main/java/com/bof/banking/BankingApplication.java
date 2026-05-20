package com.bof.banking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bank of Fiji Online Banking System
 * CS415 Advanced Software Engineering - Semester I, 2026
 *
 * Entry point for the Spring Boot backend application.
 */
@EnableScheduling
@SpringBootApplication
public class BankingApplication {

    /**
     * Handles main.
     * @param args application startup arguments.
     */
    public static void main(String[] args) {
        SpringApplication.run(BankingApplication.class, args);
    }
}
