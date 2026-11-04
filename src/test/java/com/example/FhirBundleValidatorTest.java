package com.example;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FhirBundleValidatorTest {

    @Test
    void validatesMappedSampleBundle() throws Exception {
        String input = Files.readString(
                Path.of("src/main/resources/patient_raw.json")
        );
        String bundleJson = HealthMapper.map(input);

        assertTrue(FhirBundleValidator.validateWithReport(bundleJson).successful());
    }

    @Test
    void rejectsInvalidObservationStatus() throws Exception {
        String input = Files.readString(
                Path.of("src/main/resources/patient_raw.json")
        );
        String bundleJson = HealthMapper.map(input)
                .replaceFirst("\"status\":\"final\"", "\"status\":\"invalid-status\"");

        ValidationReport report =
                FhirBundleValidator.validateWithReport(bundleJson);

        assertFalse(report.successful());
        assertTrue(report.issues().stream()
                .anyMatch(issue -> issue.message().contains("invalid-status")));
    }

    @Test
    void reportsMalformedJson() {
        ValidationReport report =
                FhirBundleValidator.validateWithReport("{not-json");

        assertFalse(report.successful());
        assertTrue(report.issues().stream()
                .anyMatch(issue -> "FATAL".equals(issue.severity())));
    }
}
