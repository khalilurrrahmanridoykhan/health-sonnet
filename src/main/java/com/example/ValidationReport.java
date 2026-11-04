package com.example;

import java.util.List;

public record ValidationReport(
        boolean successful,
        List<ValidationIssue> issues
) {
    public static ValidationReport success() {
        return new ValidationReport(true, List.of());
    }

    public static ValidationReport failure(List<ValidationIssue> issues) {
        return new ValidationReport(false, issues);
    }

    public record ValidationIssue(
            String severity,
            String location,
            String message
    ) {
    }
}
