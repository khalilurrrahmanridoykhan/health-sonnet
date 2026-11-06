package com.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dhis2")
public class Dhis2SyncController {

    private final Dhis2Client dhis2Client;
    private final Dhis2Properties properties;
    private final ObjectMapper objectMapper;
    private final boolean validationEnabled;

    public Dhis2SyncController(
            Dhis2Properties properties,
            ObjectMapper objectMapper,
            @Value("${app.validation.enabled}") boolean validationEnabled
    ) {
        this.properties = properties;
        this.dhis2Client = new Dhis2Client(properties);
        this.objectMapper = objectMapper;
        this.validationEnabled = validationEnabled;
    }

    @GetMapping(value = "/sync", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> sync(
            @RequestParam(value = "program", required = false) String program,
            @RequestParam(value = "orgUnit", required = false) String orgUnit
    ) {
        try {
            String effectiveProgram = program != null ? program : properties.getDefaultProgram();
            String effectiveOrgUnit = orgUnit != null ? orgUnit : properties.getDefaultOrgUnit();

            String dhis2Json = dhis2Client.fetchAndNormalize(effectiveProgram, effectiveOrgUnit);

            BatchResult batchResult = BatchProcessor.process(dhis2Json);

            JsonNode dhis2Raw = objectMapper.readTree(dhis2Json);
            JsonNode bundle = objectMapper.readTree(batchResult.bundleJson());

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("program", effectiveProgram);
            response.put("orgUnit", effectiveOrgUnit);
            response.put("dhis2Raw", dhis2Raw);
            response.put("bundle", bundle);
            response.put("processedTrackedEntities", batchResult.processedTrackedEntities());
            response.put("processedEvents", batchResult.processedEvents());
            response.put("warnings", batchResult.warnings());

            if (!validationEnabled) {
                // HAPI FHIR validation is too memory/CPU heavy for this host;
                // the DataSonnet-transformed bundle is still returned, just unvalidated.
                response.put("validation", null);
                response.put("validationSkipped", true);
                return ResponseEntity.ok(response);
            }

            ValidationReport validation =
                    FhirBundleValidator.validateWithReport(batchResult.bundleJson());
            response.put("validation", validation);

            return validation.successful()
                    ? ResponseEntity.ok(response)
                    : ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(response);

        } catch (Throwable throwable) {
            throwable.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Sync failed",
                    "message", throwable.getClass().getName() + ": " + throwable.getMessage()
            ));
        }
    }
}
