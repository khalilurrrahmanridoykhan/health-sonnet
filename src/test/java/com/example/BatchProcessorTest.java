package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchProcessorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void processesAllSampleRecords() throws Exception {
        String input = Files.readString(
                Path.of("src/main/resources/patient_raw.json")
        );

        BatchResult result = BatchProcessor.process(input);

        assertEquals(3, result.processedTrackedEntities().size());
        assertEquals(1, result.processedEvents().size());
        assertTrue(result.rejectedTrackedEntities().isEmpty());
        assertTrue(result.rejectedEvents().isEmpty());
    }

    @Test
    void rejectsEventWithoutMatchingPatient() throws Exception {
        String input = Files.readString(
                Path.of("src/main/resources/patient_raw.json")
        );
        ObjectNode root = (ObjectNode) objectMapper.readTree(input);
        ObjectNode event = (ObjectNode) root.path("events").get(0);
        event.put("trackedEntityInstance", "TEI-MISSING");

        BatchResult result = BatchProcessor.process(
                objectMapper.writeValueAsString(root)
        );

        assertEquals(1, result.rejectedEvents().size());
        assertEquals("EVT-001", result.rejectedEvents().get(0));
        assertTrue(result.warnings().stream()
                .anyMatch(message -> message.contains("TEI-MISSING")));
    }

    @Test
    void removesDuplicateResources() throws Exception {
        String input = Files.readString(
                Path.of("src/main/resources/patient_raw.json")
        );
        ObjectNode root = (ObjectNode) objectMapper.readTree(input);
        ArrayNode events = (ArrayNode) root.path("events");
        events.add(events.get(0).deepCopy());

        BatchResult result = BatchProcessor.process(
                objectMapper.writeValueAsString(root)
        );
        JsonNode bundle = objectMapper.readTree(result.bundleJson());

        assertEquals(1, countBundleResource(bundle, "EVT-001-encounter"));
        assertEquals(1, countBundleResource(bundle, "EVT-001-heart-rate"));
        assertEquals(1, countBundleResource(bundle, "EVT-001-blood-pressure"));
    }

    private int countBundleResource(JsonNode bundle, String resourceId) {
        int count = 0;
        for (JsonNode entry : bundle.path("entry")) {
            if (resourceId.equals(entry.path("resource").path("id").asText())) {
                count++;
            }
        }
        return count;
    }
}
