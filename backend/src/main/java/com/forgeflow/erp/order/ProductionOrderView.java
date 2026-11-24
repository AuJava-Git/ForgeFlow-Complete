package com.forgeflow.erp.order;

public record ProductionOrderView(Long id, String orderNo, Long productId, String productCode, String productName,
                                  int plannedQuantity, int goodQuantity, int defectQuantity, String status,
                                  String materialStatus) {
}
