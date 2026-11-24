package com.forgeflow.quality;

public record QualityInspectionView(long id, long reportId, long orderId, int passedQuantity,
                                    int rejectedQuantity, String result, String inspector,
                                    boolean receivedToFinishedGoods) {
}
