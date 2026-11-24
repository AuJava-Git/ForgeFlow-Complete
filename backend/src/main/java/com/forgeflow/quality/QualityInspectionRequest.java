package com.forgeflow.quality;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record QualityInspectionRequest(@NotNull @Min(0) Integer passedQuantity,
                                       @NotNull @Min(0) Integer rejectedQuantity,
                                       String remark) {
}
