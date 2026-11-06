package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class IntegrationController {

    private final ObjectMapper objectMapper;

    public IntegrationController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "UP",
                "service", "dhis2-fhir-data-integration-engine"
        );
    }

    @PostMapping(
            value = "/transform",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> transform(@RequestBody JsonNode input) {
        try {
            JsonNode bundle = objectMapper.readTree(
                    HealthMapper.map(objectMapper.writeValueAsString(input))
            );
            return ResponseEntity.ok(bundle);
        } catch (Exception exception) {
            return badRequest("Transform failed", exception);
        }
    }

    @PostMapping(
            value = "/validate",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<ValidationReport> validate(@RequestBody JsonNode bundle) {
        String bundleJson;
        try {
            bundleJson = objectMapper.writeValueAsString(bundle);
        } catch (Exception exception) {
            return ResponseEntity.badRequest().body(
                    ValidationReport.failure(java.util.List.of(
                            new ValidationReport.ValidationIssue(
                                    "FATAL",
                                    "Bundle",
                                    exception.getMessage()
                            )
                    ))
            );
        }
        ValidationReport report =
                FhirBundleValidator.validateWithReport(bundleJson);
        return report.successful()
                ? ResponseEntity.ok(report)
                : ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(report);
    }

    @PostMapping(
            value = "/process",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<?> process(@RequestBody JsonNode input) {
        try {
            BatchResult batchResult = BatchProcessor.process(
                    objectMapper.writeValueAsString(input)
            );
            ValidationReport validation = FhirBundleValidator.validateWithReport(
                    batchResult.bundleJson()
            );
            JsonNode bundle = objectMapper.readTree(batchResult.bundleJson());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("bundle", bundle);
            response.put("validation", validation);
            response.put(
                    "processedTrackedEntities",
                    batchResult.processedTrackedEntities()
            );
            response.put("processedEvents", batchResult.processedEvents());
            response.put(
                    "rejectedTrackedEntities",
                    batchResult.rejectedTrackedEntities()
            );
            response.put("rejectedEvents", batchResult.rejectedEvents());
            response.put("warnings", batchResult.warnings());

            return validation.successful()
                    ? ResponseEntity.ok(response)
                    : ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                            .body(response);
        } catch (Exception exception) {
            return badRequest("Processing failed", exception);
        }
    }

    private ResponseEntity<Map<String, String>> badRequest(
            String error,
            Exception exception
    ) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", error,
                "message", exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage()
        ));
    }
}
