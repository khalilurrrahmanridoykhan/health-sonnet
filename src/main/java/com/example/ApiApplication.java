package com.example;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@EnableConfigurationProperties(Dhis2Properties.class)
public class ApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }

    // Pays the one-time, heavy HAPI FHIR validator build cost during startup
    // (before the instance is marked live/ready), instead of on a user's
    // first request. Skipped entirely when validation is disabled (e.g. on a
    // low-memory host), so the heavy FHIR model classes never even load.
    @Bean
    public CommandLineRunner warmUpFhirValidator(
            @Value("${app.validation.enabled}") boolean validationEnabled
    ) {
        return args -> {
            if (validationEnabled) {
                FhirBundleValidator.warmUp();
            }
        };
    }
}
