package com.forgeflow.mes;

public record MesWorkOrderView(Long id, String orderNo, Long productId, int plannedQuantity,
                               int goodQuantity, int defectQuantity, String status, String materialStatus) {
}
