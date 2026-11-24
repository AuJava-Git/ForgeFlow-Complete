package com.forgeflow.erp.order;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductionOrderRequest(@NotNull @Positive Long productId, @NotNull @Positive Integer plannedQuantity) {
}
