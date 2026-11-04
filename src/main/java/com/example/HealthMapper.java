package com.example;

import com.datasonnet.Mapper;
import com.datasonnet.document.DefaultDocument;
import com.datasonnet.document.MediaTypes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.nio.file.Files;
import java.nio.file.Paths;

public class HealthMapper {

    /** Transforms a health‑JSON payload using the DataSonnet script. */
    public static String map(String inputJson) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode root = objectMapper.readTree(inputJson);
        removeInvalidNumericDataValues(root.path("events"));

        String script = Files.readString(
                Paths.get(HealthMapper.class.getClassLoader()
                .getResource("health-mapping.ds").toURI()));
        return new Mapper(script).transform(
                new DefaultDocument<>(
                        objectMapper.writeValueAsString(root),
                        MediaTypes.APPLICATION_JSON
                )).getContent();
    }

    private static void removeInvalidNumericDataValues(JsonNode events) {
        ObjectMapper objectMapper = new ObjectMapper();

        events.forEach(event -> {
            JsonNode dataValues = event.path("dataValues");
            if (!dataValues.isArray()) {
                return;
            }

            ArrayNode filtered = objectMapper.createArrayNode();
            dataValues.forEach(dataValue -> {
                String uid = dataValue.path("dataElement").asText();
                String value = dataValue.path("value").asText();

                if (!isVitalSignUid(uid) || isInteger(value)) {
                    filtered.add(dataValue);
                }
            });

            ((ArrayNode) dataValues).removeAll();
            filtered.forEach(((ArrayNode) dataValues)::add);
        });
    }

    private static boolean isVitalSignUid(String uid) {
        return "systolicUID".equals(uid)
                || "diastolicUID".equals(uid)
                || "heartRateUID".equals(uid);
    }

    private static boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

}
