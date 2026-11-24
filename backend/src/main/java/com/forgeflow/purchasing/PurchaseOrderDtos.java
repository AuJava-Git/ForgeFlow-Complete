package com.forgeflow.purchasing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.List;

public final class PurchaseOrderDtos {
    private PurchaseOrderDtos() {
    }

    public record PurchaseReceiptLineRequest(@NotNull Long materialId,
                                             @NotNull @DecimalMin(value = "0.0001") BigDecimal quantity) {
    }

    public record PurchaseReceiptRequest(@NotEmpty List<@Valid PurchaseReceiptLineRequest> items) {
    }

    public record PurchaseOrderLineView(long materialId, String materialCode, String materialName, String unit,
                                        BigDecimal orderedQuantity, BigDecimal receivedQuantity,
                                        BigDecimal outstandingQuantity) {
    }

    public record PurchaseOrderView(long id, String orderNo, long productionOrderId, String status,
                                    List<PurchaseOrderLineView> lines) {
    }
}
