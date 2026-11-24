package com.forgeflow.mes;

public record ProductionReportView(Long id, long orderId, int goodQuantity, int defectQuantity, int reportedQuantity,
                                   int goodQuantityTotal, int defectQuantityTotal, String status) {
}
