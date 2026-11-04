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
        FhirContext fhirContext = FhirContext.forR4();
        ValidationSupportChain validationSupport = new ValidationSupportChain(
                new DefaultProfileValidationSupport(fhirContext),
                new CommonCodeSystemsTerminologyService(fhirContext),
                new InMemoryTerminologyServerValidationSupport(fhirContext),
                new SnapshotGeneratingValidationSupport(fhirContext)
        );
        FhirInstanceValidator instanceValidator =
                new FhirInstanceValidator(validationSupport);
        FhirValidator validator = fhirContext.newValidator();
        validator.registerValidatorModule(instanceValidator);

        Bundle bundle;
        try {
            bundle = (Bundle) fhirContext.newJsonParser()
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
                validator.validateWithResult(bundle);

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
