package com.forgeflow.erp.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProductRequest(
        @NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9_-]+") String code,
        @NotBlank @Size(max = 120) String name,
        @Pattern(regexp = "FINISHED|MATERIAL") String type,
        @NotBlank @Size(max = 16) String unit,
        boolean enabled) {
}
