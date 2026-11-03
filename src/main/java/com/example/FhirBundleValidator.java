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

public class FhirBundleValidator {

    public static boolean validate(String bundleJson) {
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
            System.out.println("FHIR validation: FAILED");
            System.out.printf(
                    "FHIR parsing error: %s%n",
                    exception.getMessage()
            );
            return false;
        }

        ValidationResult result =
                validator.validateWithResult(bundle);

        System.out.println("FHIR validation: " +
                (result.isSuccessful() ? "SUCCESS" : "FAILED"));

        for (SingleValidationMessage message : result.getMessages()) {
            if (message.getSeverity() == ResultSeverityEnum.ERROR
                    || message.getSeverity() == ResultSeverityEnum.FATAL) {
                System.out.printf(
                        "%s: %s%n",
                        message.getLocationString(),
                        message.getMessage()
                );
            }
        }

        return result.isSuccessful();
    }
}
