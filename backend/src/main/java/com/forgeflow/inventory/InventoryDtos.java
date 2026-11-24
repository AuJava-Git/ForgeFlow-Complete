package com.forgeflow.inventory;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public final class InventoryDtos {
    private InventoryDtos() {
    }

    public record InventoryBalanceView(long productId, String code, String name, String productType,
                                       String unit, BigDecimal onHandQuantity, BigDecimal reservedQuantity,
                                       BigDecimal availableQuantity) {
    }

    public record MaterialRequirementView(long materialId, String code, String name, String unit,
                                          BigDecimal requiredQuantity, BigDecimal reservedQuantity,
                                          BigDecimal issuedQuantity, BigDecimal availableQuantity,
                                          BigDecimal shortageQuantity) {
    }

    public record InventoryReceiptRequest(@NotNull Long productId,
                                          @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity,
                                          @NotBlank String reason) {
    }
}
