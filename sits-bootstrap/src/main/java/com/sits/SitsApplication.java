package com.sits;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SITS - Smart Inventory Transfer System
 * Main application entry point.
 */
@SpringBootApplication(scanBasePackages = "com.sits")
public class SitsApplication {

    public static void main(String[] args) {
        SpringApplication.run(SitsApplication.class, args);
    }

}
