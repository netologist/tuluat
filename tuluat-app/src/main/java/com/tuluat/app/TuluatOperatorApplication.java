package com.tuluat.app;

import io.javaoperatorsdk.operator.springboot.starter.OperatorAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = "com.tuluat", exclude = {OperatorAutoConfiguration.class})
@EnableJpaRepositories(basePackages = "com.tuluat.engine.repository")
@EntityScan(basePackages = "com.tuluat.engine.entity")
public class TuluatOperatorApplication {

    public static void main(String[] args) {
        // Enable virtual threads for Spring Boot runtime if available
        System.setProperty("spring.threads.virtual.enabled", "true");
        SpringApplication.run(TuluatOperatorApplication.class, args);
    }
}
