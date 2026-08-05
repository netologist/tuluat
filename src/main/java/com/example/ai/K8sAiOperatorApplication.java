package com.example.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class K8sAiOperatorApplication {

    public static void main(String[] args) {
        // Enable virtual threads for Spring Boot runtime if available
        System.setProperty("spring.threads.virtual.enabled", "true");
        SpringApplication.run(K8sAiOperatorApplication.class, args);
    }
}
