package com.mcp.tools.model;

import java.util.ArrayList;
import java.util.List;

public record ValidationResult(
        boolean valid,
        List<String> errors,
        List<String> warnings
) {
    public static ValidationResult success() {
        return new ValidationResult(true, List.of(), List.of());
    }

    public static ValidationResult success(List<String> warnings) {
        return new ValidationResult(true, List.of(), warnings);
    }

    public static ValidationResult failure(List<String> errors) {
        return new ValidationResult(false, errors, List.of());
    }

    public static ValidationResult of(List<String> errors, List<String> warnings) {
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    public ValidationResult merge(ValidationResult other) {
        var allErrors = new ArrayList<>(errors);
        allErrors.addAll(other.errors);
        var allWarnings = new ArrayList<>(warnings);
        allWarnings.addAll(other.warnings);
        return new ValidationResult(allErrors.isEmpty(), allErrors, allWarnings);
    }
}