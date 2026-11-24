package com.forgeflow.erp.product;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record BomLineRequest(@NotNull @Positive Long materialId, @NotNull @Positive BigDecimal quantity) {
}
