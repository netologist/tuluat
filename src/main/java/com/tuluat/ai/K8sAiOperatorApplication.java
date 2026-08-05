package com.tuluat.ai;

import io.javaoperatorsdk.operator.springboot.starter.OperatorAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {OperatorAutoConfiguration.class})
public class K8sAiOperatorApplication {

    public static void main(String[] args) {
        // Enable virtual threads for Spring Boot runtime if available
        System.setProperty("spring.threads.virtual.enabled", "true");
        SpringApplication.run(K8sAiOperatorApplication.class, args);
    }
}
