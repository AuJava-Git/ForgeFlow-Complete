package com.forgeflow.erp.order;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BomRequirementCalculator {
    private BomRequirementCalculator() {
    }

    public static BigDecimal totalRequired(BigDecimal quantityPerUnit, int plannedQuantity) {
        if (quantityPerUnit == null || quantityPerUnit.signum() <= 0 || plannedQuantity <= 0) {
            throw new IllegalArgumentException("BOM用量和计划数量必须为正数");
        }
        return quantityPerUnit.multiply(BigDecimal.valueOf(plannedQuantity))
                .setScale(4, RoundingMode.HALF_UP);
    }
}
