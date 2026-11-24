package com.forgeflow.mes;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ProductionReportRequest(@Min(0) int goodQuantity, @Min(0) int defectQuantity,
                                      @Size(max = 500) String remark) {
}
