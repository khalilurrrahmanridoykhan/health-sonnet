package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * Fetches live Tracker data from a DHIS2 instance and normalizes it into the
 * JSON shape HealthMapper/health-mapping.ds already expects (trackedEntities
 * with trackedEntityInstance, events with eventDate), including aliasing real
 * DHIS2 attribute/data-element UIDs to the mapper's placeholder UID keys.
 */
public class Dhis2Client {

    // Real DHIS2 UID -> placeholder UID the .ds script already understands.
    // Child Programme (IpHINAT79UW) only has First name / Last name / Gender;
    // it has no date-of-birth or vitals data elements.
    private static final Map<String, String> UID_ALIASES = Map.of(
            "w75KJ2mc4zz", "firstNameUID",
            "zDhUuAYrxNC", "surnameUID",
            "cejWyOfXge6", "sexUID"
    );

    private final Dhis2Properties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public Dhis2Client(Dhis2Properties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public String fetchAndNormalize(String program, String orgUnit) throws Exception {
        // Fetch tracked entities with their enrollments/events nested in one call,
        // instead of two separately-paginated calls, so every event is guaranteed
        // to stay correlated with the tracked entity it belongs to.
        JsonNode trackedEntities = get(
                "/api/tracker/trackedEntities.json"
                        + "?program=" + program
                        + "&orgUnit=" + orgUnit
                        + "&fields=trackedEntity,orgUnit,attributes[attribute,value],"
                        + "enrollments[events[event,programStage,orgUnit,occurredAt,dataValues[dataElement,value]]]"
                        + "&pageSize=50"
        ).path("trackedEntities");

        ObjectNode normalized = objectMapper.createObjectNode();
        normalized.set("trackedEntities", normalizeTrackedEntities(trackedEntities));
        normalized.set("events", extractEvents(trackedEntities));
        return objectMapper.writeValueAsString(normalized);
    }

    private ArrayNode normalizeTrackedEntities(JsonNode source) {
        ArrayNode result = objectMapper.createArrayNode();
        for (JsonNode entity : source) {
            ObjectNode normalized = objectMapper.createObjectNode();
            normalized.put("trackedEntityInstance", entity.path("trackedEntity").asText());
            normalized.put("orgUnit", entity.path("orgUnit").asText());
            normalized.set("attributes", aliasUids(entity.path("attributes"), "attribute"));
            result.add(normalized);
        }
        return result;
    }

    /** Flattens each tracked entity's nested enrollments[events[...]] into one events array. */
    private ArrayNode extractEvents(JsonNode trackedEntities) {
        ArrayNode result = objectMapper.createArrayNode();
        for (JsonNode entity : trackedEntities) {
            String trackedEntityInstance = entity.path("trackedEntity").asText();

            for (JsonNode enrollment : entity.path("enrollments")) {
                for (JsonNode event : enrollment.path("events")) {
                    ObjectNode normalized = objectMapper.createObjectNode();
                    normalized.put("event", event.path("event").asText());
                    normalized.put("trackedEntityInstance", trackedEntityInstance);
                    normalized.put("programStage", event.path("programStage").asText());
                    normalized.put("orgUnit", event.path("orgUnit").asText());
                    String occurredAt = event.path("occurredAt").asText();
                    normalized.put("eventDate", occurredAt.length() >= 10 ? occurredAt.substring(0, 10) : occurredAt);
                    normalized.set("dataValues", aliasUids(event.path("dataValues"), "dataElement"));
                    result.add(normalized);
                }
            }
        }
        return result;
    }

    /** Renames real DHIS2 UIDs in the given field to the mapper's placeholder UID keys. */
    private ArrayNode aliasUids(JsonNode items, String uidField) {
        ArrayNode result = objectMapper.createArrayNode();
        for (JsonNode item : items) {
            String realUid = item.path(uidField).asText();
            String aliasedUid = UID_ALIASES.getOrDefault(realUid, realUid);

            ObjectNode copy = objectMapper.createObjectNode();
            copy.put(uidField, aliasedUid);
            copy.put("value", item.path("value").asText());
            result.add(copy);
        }
        return result;
    }

    private JsonNode get(String path) throws Exception {
        String credentials = Base64.getEncoder().encodeToString(
                (properties.getUsername() + ":" + properties.getPassword())
                        .getBytes(StandardCharsets.UTF_8)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + path))
                .header("Authorization", "Basic " + credentials)
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Dhis2ConnectionException(
                    "DHIS2 request failed: HTTP " + response.statusCode() + " for " + path
            );
        }

        return objectMapper.readTree(response.body());
    }
}
