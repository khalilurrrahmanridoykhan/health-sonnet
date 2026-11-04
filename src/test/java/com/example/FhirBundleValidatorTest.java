package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FhirBundleValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

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

    @Test
    void rejectsObservationWithoutCode() throws Exception {
        String input = Files.readString(
                Path.of("src/main/resources/patient_raw.json")
        );
        JsonNode bundle = objectMapper.readTree(HealthMapper.map(input));
        findObservation(bundle, "EVT-001-blood-pressure")
                .remove("code");

        ValidationReport report = FhirBundleValidator.validateWithReport(
                objectMapper.writeValueAsString(bundle)
        );

        assertFalse(report.successful());
    }

    @Test
    void rejectsInvalidPatientBirthDate() throws Exception {
        String input = Files.readString(
                Path.of("src/main/resources/patient_raw.json")
        );
        JsonNode bundle = objectMapper.readTree(HealthMapper.map(input));
        findResource(bundle, "Patient", "TEI-001")
                .put("birthDate", "not-a-date");

        ValidationReport report = FhirBundleValidator.validateWithReport(
                objectMapper.writeValueAsString(bundle)
        );

        assertFalse(report.successful());
    }

    private ObjectNode findObservation(JsonNode bundle, String id) {
        for (JsonNode entry : bundle.path("entry")) {
            JsonNode resource = entry.path("resource");
            if ("Observation".equals(resource.path("resourceType").asText())
                    && id.equals(resource.path("id").asText())) {
                return (ObjectNode) resource;
            }
        }
        throw new AssertionError("Observation not found: " + id);
    }

    private ObjectNode findResource(
            JsonNode bundle,
            String resourceType,
            String id
    ) {
        for (JsonNode entry : bundle.path("entry")) {
            JsonNode resource = entry.path("resource");
            if (resourceType.equals(resource.path("resourceType").asText())
                    && id.equals(resource.path("id").asText())) {
                return (ObjectNode) resource;
            }
        }
        throw new AssertionError(resourceType + " not found: " + id);
    }
}
