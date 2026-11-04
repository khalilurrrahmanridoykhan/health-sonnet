package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class BatchProcessor {

    private BatchProcessor() {
    }

    public static BatchResult process(String inputJson) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode input = objectMapper.readTree(inputJson);

        List<String> processedTrackedEntities = new ArrayList<>();
        List<String> rejectedTrackedEntities = new ArrayList<>();
        List<String> processedEvents = new ArrayList<>();
        List<String> rejectedEvents = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (JsonNode patient : input.path("trackedEntities")) {
            String id = patient.path("trackedEntityInstance").asText();
            if (id.isBlank()) {
                rejectedTrackedEntities.add("<missing trackedEntityInstance>");
            } else {
                processedTrackedEntities.add(id);
            }
        }

        Set<String> patientIds = new LinkedHashSet<>(processedTrackedEntities);
        for (JsonNode event : input.path("events")) {
            String id = event.path("event").asText();
            String subjectId = event.path("trackedEntityInstance").asText();

            if (id.isBlank()) {
                rejectedEvents.add("<missing event>");
            } else if (subjectId.isBlank() || !patientIds.contains(subjectId)) {
                rejectedEvents.add(id);
                warnings.add("Event " + id
                        + " references missing Patient " + subjectId);
            } else {
                processedEvents.add(id);
            }
        }

        String bundleJson = removeDuplicateEntries(
                HealthMapper.map(inputJson),
                objectMapper
        );
        ObjectNode bundle = (ObjectNode) objectMapper.readTree(bundleJson);
        removeEntriesWithMissingPatientReferences(
                bundle,
                patientIds,
                warnings,
                objectMapper
        );

        return new BatchResult(
                objectMapper.writeValueAsString(bundle),
                List.copyOf(processedTrackedEntities),
                List.copyOf(processedEvents),
                List.copyOf(rejectedTrackedEntities),
                List.copyOf(rejectedEvents),
                List.copyOf(warnings)
        );
    }

    private static String removeDuplicateEntries(
            String bundleJson,
            ObjectMapper objectMapper
    ) throws Exception {
        ObjectNode bundle = (ObjectNode) objectMapper.readTree(bundleJson);
        ArrayNode entries = (ArrayNode) bundle.path("entry");
        ArrayNode uniqueEntries = objectMapper.createArrayNode();
        Set<String> resourceKeys = new LinkedHashSet<>();

        for (JsonNode entry : entries) {
            JsonNode resource = entry.path("resource");
            String key = resource.path("resourceType").asText()
                    + "/"
                    + resource.path("id").asText();
            if (!resourceKeys.contains(key)) {
                resourceKeys.add(key);
                uniqueEntries.add(entry);
            }
        }

        bundle.set("entry", uniqueEntries);
        return objectMapper.writeValueAsString(bundle);
    }

    private static void removeEntriesWithMissingPatientReferences(
            ObjectNode bundle,
            Set<String> patientIds,
            List<String> warnings,
            ObjectMapper objectMapper
    ) {
        ArrayNode filteredEntries =
                objectMapper.createArrayNode();

        bundle.path("entry").forEach(entry -> {
            JsonNode resource = entry.path("resource");
            String reference = resource.path("subject").path("reference").asText();

            if (reference.isBlank()) {
                filteredEntries.add(entry);
            } else {
                String patientId = reference.startsWith("Patient/")
                        ? reference.substring("Patient/".length())
                        : "";
                if (patientIds.contains(patientId)) {
                    filteredEntries.add(entry);
                } else {
                    warnings.add("Removed resource "
                            + resource.path("resourceType").asText() + "/"
                            + resource.path("id").asText()
                            + " because Patient " + patientId + " is missing");
                }
            }
        });

        bundle.set("entry", filteredEntries);
    }
}
