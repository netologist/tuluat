package com.tuluat.app;

import io.javaoperatorsdk.operator.springboot.starter.OperatorAutoConfiguration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@EntityScan(basePackages = "com.tuluat.engine.entity")
@EnableJpaRepositories(basePackages = "com.tuluat.engine.repository")
@SpringBootApplication(
        scanBasePackages = "com.tuluat",
        exclude = {OperatorAutoConfiguration.class}
)
public class TuluatOperatorApplication {
    public static void main(String[] args) {
        System.setProperty("spring.threads.virtual.enabled", "true");
        SpringApplication.run(TuluatOperatorApplication.class, args);
    }
}

