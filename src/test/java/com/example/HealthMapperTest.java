package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HealthMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsSampleDhis2PayloadToCollectionBundle() throws Exception {
        String input = Files.readString(
                Path.of("src/main/resources/patient_raw.json")
        );

        String bundleJson = HealthMapper.map(input);
        JsonNode bundle = objectMapper.readTree(bundleJson);

        assertEquals("Bundle", bundle.path("resourceType").asText());
        assertEquals("collection", bundle.path("type").asText());
        assertTrue(bundle.path("entry").isArray());
        assertTrue(bundle.path("entry").size() >= 1);
    }

    @Test
    void mapsExpectedResourcesAndReferences() throws Exception {
        JsonNode bundle = mapSampleBundle();
        List<JsonNode> resources = bundleResources(bundle);

        assertEquals(3, countResources(resources, "Patient"));
        assertEquals(1, countResources(resources, "Encounter"));
        assertEquals(2, countResourcesWithId(resources, "Observation"));

        JsonNode patient = findResource(resources, "Patient", "TEI-001");
        assertEquals("male", patient.path("gender").asText());

        JsonNode encounter = findResource(resources, "Encounter", "EVT-001-encounter");
        assertEquals("Patient/TEI-001",
                encounter.path("subject").path("reference").asText());

        JsonNode bloodPressure = findResource(
                resources,
                "Observation",
                "EVT-001-blood-pressure"
        );
        assertEquals("Patient/TEI-001",
                bloodPressure.path("subject").path("reference").asText());
        assertEquals(2, bloodPressure.path("component").size());
        assertEquals(120, bloodPressure.path("component").get(0)
                .path("valueQuantity").path("value").intValue());

        JsonNode heartRate = findResource(
                resources,
                "Observation",
                "EVT-001-heart-rate"
        );
        assertEquals(72, heartRate.path("valueQuantity").path("value").intValue());
        assertEquals("Patient/TEI-001",
                heartRate.path("subject").path("reference").asText());
    }

    @Test
    void looksUpAttributesAndDataValuesIndependentOfArrayOrder() throws Exception {
        JsonNode input = readSampleInput();
        ArrayNode firstPatientAttributes =
                (ArrayNode) input.path("trackedEntities").get(0).path("attributes");
        reverse(firstPatientAttributes);

        ArrayNode dataValues =
                (ArrayNode) input.path("events").get(0).path("dataValues");
        reverse(dataValues);

        JsonNode bundle = map(objectMapper.writeValueAsString(input));
        List<JsonNode> resources = bundleResources(bundle);

        JsonNode patient = findResource(resources, "Patient", "TEI-001");
        assertEquals("Ahmed Hussein",
                patient.path("name").get(0).path("text").asText());
        assertEquals("1985-04-12", patient.path("birthDate").asText());

        JsonNode heartRate = findResource(
                resources,
                "Observation",
                "EVT-001-heart-rate"
        );
        assertEquals(72, heartRate.path("valueQuantity").path("value").intValue());
    }

    @Test
    void handlesMissingOptionalValues() throws Exception {
        JsonNode input = readSampleInput();
        removeDataValue(input.path("events").get(0), "heartRateUID");

        JsonNode patientWithMissingDate = input.path("trackedEntities").get(2);
        JsonNode bundle = map(objectMapper.writeValueAsString(input));
        List<JsonNode> resources = bundleResources(bundle);

        JsonNode patient = findResource(resources, "Patient", "TEI-003");
        assertTrue(patientWithMissingDate.path("attributes").isArray());
        assertTrue(patient.path("birthDate").isMissingNode());

        assertEquals(0, findResources(resources, "Observation")
                .stream()
                .filter(resource -> resource.path("id")
                        .asText().equals("EVT-001-heart-rate"))
                .count());
    }

    private JsonNode readSampleInput() throws Exception {
        return objectMapper.readTree(Files.readString(
                Path.of("src/main/resources/patient_raw.json")
        ));
    }

    private JsonNode mapSampleBundle() throws Exception {
        return map(Files.readString(
                Path.of("src/main/resources/patient_raw.json")
        ));
    }

    private JsonNode map(String input) throws Exception {
        return objectMapper.readTree(HealthMapper.map(input));
    }

    private List<JsonNode> bundleResources(JsonNode bundle) {
        List<JsonNode> resources = new ArrayList<>();
        bundle.path("entry").forEach(entry ->
                resources.add(entry.path("resource"))
        );
        return resources;
    }

    private int countResources(List<JsonNode> resources, String resourceType) {
        return findResources(resources, resourceType).size();
    }

    private int countResourcesWithId(List<JsonNode> resources, String resourceType) {
        return (int) findResources(resources, resourceType).stream()
                .filter(resource -> resource.has("id"))
                .count();
    }

    private List<JsonNode> findResources(
            List<JsonNode> resources,
            String resourceType
    ) {
        List<JsonNode> matches = new ArrayList<>();
        resources.forEach(resource -> {
            if (resourceType.equals(resource.path("resourceType").asText())) {
                matches.add(resource);
            }
        });
        return matches;
    }

    private JsonNode findResource(
            List<JsonNode> resources,
            String resourceType,
            String id
    ) {
        return findResources(resources, resourceType).stream()
                .filter(resource -> id.equals(resource.path("id").asText()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        resourceType + "/" + id + " was not found"
                ));
    }

    private void reverse(ArrayNode array) {
        List<JsonNode> values = new ArrayList<>();
        array.forEach(values::add);
        array.removeAll();
        for (int index = values.size() - 1; index >= 0; index--) {
            array.add(values.get(index));
        }
    }

    private void removeDataValue(JsonNode event, String dataElementUid) {
        ArrayNode dataValues = (ArrayNode) event.path("dataValues");
        List<JsonNode> remaining = new ArrayList<>();
        dataValues.forEach(value -> {
            if (!dataElementUid.equals(
                    value.path("dataElement").asText()
            )) {
                remaining.add(value);
            }
        });
        dataValues.removeAll();
        remaining.forEach(dataValues::add);
    }
}
