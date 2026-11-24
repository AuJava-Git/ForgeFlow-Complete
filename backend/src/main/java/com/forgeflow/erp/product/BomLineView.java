package com.forgeflow.erp.product;

import java.math.BigDecimal;

public record BomLineView(Long materialId, String materialCode, String materialName, String unit,
                          BigDecimal quantity) {
}
