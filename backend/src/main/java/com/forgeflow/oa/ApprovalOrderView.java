package com.forgeflow.oa;

public record ApprovalOrderView(Long id, String orderNo, Long productId, int plannedQuantity, String status) {
}
