package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ApiApplication.class)
@AutoConfigureMockMvc
class IntegrationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private final String samplePayload = Files.readString(
            Path.of("src/main/resources/patient_raw.json")
    );

    IntegrationControllerTest() throws Exception {
    }

    @Test
    void healthEndpointReturnsUp() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value(
                        "dhis2-fhir-data-integration-engine"
                ));
    }

    @Test
    void transformEndpointReturnsCollectionBundle() throws Exception {
        mockMvc.perform(post("/api/v1/transform")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(samplePayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceType").value("Bundle"))
                .andExpect(jsonPath("$.type").value("collection"))
                .andExpect(jsonPath("$.entry").isArray());
    }

    @Test
    void validateEndpointAcceptsMappedBundle() throws Exception {
        JsonNode bundle = objectMapper.readTree(HealthMapper.map(samplePayload));

        mockMvc.perform(post("/api/v1/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bundle)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successful").value(true));
    }

    @Test
    void processEndpointReturnsBatchDetails() throws Exception {
        mockMvc.perform(post("/api/v1/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(samplePayload))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_JSON
                ))
                .andExpect(jsonPath("$.bundle.resourceType").value("Bundle"))
                .andExpect(jsonPath("$.validation.successful").value(true))
                .andExpect(jsonPath("$.processedTrackedEntities.length()").value(3))
                .andExpect(jsonPath("$.processedEvents.length()").value(1));
    }
}
