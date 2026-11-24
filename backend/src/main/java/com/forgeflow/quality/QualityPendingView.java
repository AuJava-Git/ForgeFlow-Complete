package com.forgeflow.quality;

public record QualityPendingView(long reportId, long orderId, String orderNo, int goodQuantity,
                                 int defectQuantity, String operator, String remark) {
}
