package com.forgeflow.purchasing;

import com.forgeflow.erp.order.ProductionOrder;
import com.forgeflow.erp.order.ProductionOrderMapper;
import com.forgeflow.erp.product.Product;
import com.forgeflow.erp.product.ProductMapper;
import com.forgeflow.inventory.InventoryDtos.MaterialRequirementView;
import com.forgeflow.inventory.InventoryService;
import com.forgeflow.platform.error.BusinessRuleException;
import com.forgeflow.purchasing.PurchaseOrderDtos.PurchaseOrderView;
import com.forgeflow.purchasing.PurchaseOrderDtos.PurchaseReceiptLineRequest;
import com.forgeflow.purchasing.PurchaseOrderDtos.PurchaseReceiptRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PurchasingServiceTest {
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final ProductionOrderMapper orderMapper = mock(ProductionOrderMapper.class);
    private final PurchaseOrderMapper purchaseOrderMapper = mock(PurchaseOrderMapper.class);
    private final PurchaseOrderLineMapper lineMapper = mock(PurchaseOrderLineMapper.class);
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final PurchasingService service = new PurchasingService(
            inventoryService, orderMapper, purchaseOrderMapper, lineMapper, productMapper);

    @Test
    void createsPurchaseOrderForShortageQuantityOnly() {
        ProductionOrder productionOrder = new ProductionOrder();
        productionOrder.setId(50L);
        productionOrder.setStatus("APPROVED");
        when(orderMapper.selectById(50L)).thenReturn(productionOrder);
        when(purchaseOrderMapper.selectByProductionOrderId(50L)).thenReturn(null);
        when(inventoryService.requirements(50L)).thenReturn(List.of(
                new MaterialRequirementView(11L, "RM-11", "钢板", "kg", new BigDecimal("10.0000"),
                        BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("8.5000"), new BigDecimal("1.5000")),
                new MaterialRequirementView(12L, "RM-12", "螺丝", "pcs", new BigDecimal("20.0000"),
                        BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("40.0000"), BigDecimal.ZERO)));
        when(purchaseOrderMapper.insert(any(PurchaseOrder.class))).thenAnswer(invocation -> {
            PurchaseOrder purchaseOrder = invocation.getArgument(0);
            purchaseOrder.setId(90L);
            return 1;
        });
        when(lineMapper.insert(any(PurchaseOrderLine.class))).thenReturn(1);
        Product steel = new Product();
        steel.setId(11L);
        steel.setCode("RM-11");
        steel.setName("钢板");
        steel.setUnit("kg");
        when(productMapper.selectById(11L)).thenReturn(steel);

        PurchaseOrderView result = service.createForShortages(50L, "planner");

        assertThat(result.status()).isEqualTo("OPEN");
        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).orderedQuantity()).isEqualByComparingTo("1.5000");
        assertThat(result.lines().get(0).materialCode()).isEqualTo("RM-11");
    }

    @Test
    void rejectsReceiptAboveOutstandingQuantityBeforeReceivingAnything() {
        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setId(90L);
        purchaseOrder.setProductionOrderId(50L);
        purchaseOrder.setStatus("OPEN");
        when(purchaseOrderMapper.selectById(90L)).thenReturn(purchaseOrder);
        PurchaseOrderLine line = new PurchaseOrderLine();
        line.setId(91L);
        line.setPurchaseOrderId(90L);
        line.setMaterialId(11L);
        line.setOrderedQuantity(new BigDecimal("2.0000"));
        line.setReceivedQuantity(new BigDecimal("1.0000"));
        when(lineMapper.selectByPurchaseOrderId(90L)).thenReturn(List.of(line));

        assertThatThrownBy(() -> service.receive(90L,
                new PurchaseReceiptRequest(List.of(new PurchaseReceiptLineRequest(11L, new BigDecimal("2.0000")))),
                "planner"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("RECEIPT_EXCEEDS_OUTSTANDING"));
        verify(inventoryService, never()).receivePurchasedMaterial(anyLong(), anyLong(), any(), any());
    }
}
