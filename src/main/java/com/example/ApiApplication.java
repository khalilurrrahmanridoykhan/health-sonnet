package com.example;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(Dhis2Properties.class)
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }

    // Pays the one-time, heavy HAPI FHIR validator build cost during startup
    // (before the instance is marked live/ready), instead of on a user's
    // first request - which previously risked a timeout or OOM on low-memory hosts.
    @Bean
    public CommandLineRunner warmUpFhirValidator() {
        return args -> FhirBundleValidator.warmUp();
    }
}
