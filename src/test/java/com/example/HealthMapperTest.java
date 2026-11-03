package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

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
}
