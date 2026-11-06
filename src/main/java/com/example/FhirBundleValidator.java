package com.example;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;
import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.r4.model.Bundle;

import java.util.ArrayList;
import java.util.List;

public class FhirBundleValidator {

    // Built once and reused: constructing this chain scans hundreds of FHIR
    // model classes and is far too memory/CPU heavy to redo on every request,
    // especially on a memory-constrained host.
    private static final FhirContext FHIR_CONTEXT = FhirContext.forR4();
    private static final FhirValidator VALIDATOR = buildValidator();

    private static FhirValidator buildValidator() {
        ValidationSupportChain validationSupport = new ValidationSupportChain(
                new DefaultProfileValidationSupport(FHIR_CONTEXT),
                new CommonCodeSystemsTerminologyService(FHIR_CONTEXT),
                new InMemoryTerminologyServerValidationSupport(FHIR_CONTEXT),
                new SnapshotGeneratingValidationSupport(FHIR_CONTEXT)
        );
        FhirInstanceValidator instanceValidator = new FhirInstanceValidator(validationSupport);
        FhirValidator validator = FHIR_CONTEXT.newValidator();
        validator.registerValidatorModule(instanceValidator);
        return validator;
    }

    /** Forces the static initializer above to run now instead of on the first real request. */
    public static void warmUp() {
    }

    public static boolean validate(String bundleJson) {
        ValidationReport report = validateWithReport(bundleJson);

        System.out.println("FHIR validation: " +
                (report.successful() ? "SUCCESS" : "FAILED"));

        for (ValidationReport.ValidationIssue issue : report.issues()) {
            System.out.printf(
                    "%s: %s - %s%n",
                    issue.location(),
                    issue.severity(),
                    issue.message()
            );
        }

        return report.successful();
    }

    public static ValidationReport validateWithReport(String bundleJson) {
        Bundle bundle;
        try {
            bundle = (Bundle) FHIR_CONTEXT.newJsonParser()
                    .parseResource(bundleJson);
        } catch (DataFormatException exception) {
            return ValidationReport.failure(List.of(
                    new ValidationReport.ValidationIssue(
                            "FATAL",
                            "Bundle",
                            exception.getMessage()
                    )
            ));
        }

        ValidationResult result =
                VALIDATOR.validateWithResult(bundle);

        List<ValidationReport.ValidationIssue> issues = new ArrayList<>();
        for (SingleValidationMessage message : result.getMessages()) {
            if (message.getSeverity() == ResultSeverityEnum.ERROR
                    || message.getSeverity() == ResultSeverityEnum.FATAL) {
                issues.add(new ValidationReport.ValidationIssue(
                        message.getSeverity().name(),
                        message.getLocationString(),
                        message.getMessage()
                ));
            }
        }

        return result.isSuccessful()
                ? ValidationReport.success()
                : ValidationReport.failure(issues);
    }
}
