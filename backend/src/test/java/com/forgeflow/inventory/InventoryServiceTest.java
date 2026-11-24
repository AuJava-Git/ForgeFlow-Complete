package com.forgeflow.inventory;

import com.forgeflow.erp.order.ProductionOrder;
import com.forgeflow.erp.order.ProductionOrderMapper;
import com.forgeflow.erp.product.ProductMapper;
import com.forgeflow.platform.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InventoryServiceTest {
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final ProductionOrderMapper orderMapper = mock(ProductionOrderMapper.class);
    private final OrderMaterialRequirementMapper requirementMapper = mock(OrderMaterialRequirementMapper.class);
    private final InventoryBalanceMapper balanceMapper = mock(InventoryBalanceMapper.class);
    private final InventoryTransactionMapper transactionMapper = mock(InventoryTransactionMapper.class);
    private final InventoryService service = new InventoryService(
            productMapper, orderMapper, requirementMapper, balanceMapper, transactionMapper);

    @Test
    void shortageOnOneMaterialLeavesAllMaterialsUnreserved() {
        when(orderMapper.selectById(50L)).thenReturn(order("NOT_READY"));
        when(requirementMapper.selectByOrderId(50L)).thenReturn(List.of(
                requirement(1L, 11L, "5.0000"), requirement(2L, 12L, "3.0000")));
        when(balanceMapper.lockByProductId(11L)).thenReturn(balance(11L, "10.0000", "0.0000"));
        when(balanceMapper.lockByProductId(12L)).thenReturn(balance(12L, "2.0000", "0.0000"));

        assertThatThrownBy(() -> service.reserve(50L, "planner"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("INSUFFICIENT_STOCK"));

        verify(balanceMapper, never()).reserveIfAvailable(anyLong(), any(BigDecimal.class));
        verify(requirementMapper, never()).updateReserved(anyLong(), any(BigDecimal.class));
        verify(transactionMapper, never()).insert(any(InventoryTransaction.class));
        verify(orderMapper, never()).updateMaterialStatus(50L, "NOT_READY", "RESERVED");
    }

    @Test
    void issueConsumesReservedMaterialAndMarksOrderReadyForMes() {
        when(orderMapper.selectById(50L)).thenReturn(order("RESERVED"));
        when(requirementMapper.selectByOrderId(50L)).thenReturn(List.of(requirement(1L, 11L, "5.0000", "5.0000")));
        when(balanceMapper.lockByProductId(11L)).thenReturn(balance(11L, "10.0000", "5.0000"));
        when(balanceMapper.issueReserved(11L, new BigDecimal("5.0000"))).thenReturn(1);
        when(requirementMapper.updateIssued(1L, new BigDecimal("5.0000"))).thenReturn(1);
        when(orderMapper.updateMaterialStatus(50L, "RESERVED", "ISSUED")).thenReturn(1);
        when(transactionMapper.insert(any(InventoryTransaction.class))).thenReturn(1);

        service.issue(50L, "planner");

        verify(balanceMapper).issueReserved(11L, new BigDecimal("5.0000"));
        verify(requirementMapper).updateIssued(1L, new BigDecimal("5.0000"));
        verify(orderMapper).updateMaterialStatus(50L, "RESERVED", "ISSUED");
        verify(transactionMapper).insert(any(InventoryTransaction.class));
    }

    private static ProductionOrder order(String materialStatus) {
        ProductionOrder order = new ProductionOrder();
        order.setId(50L);
        order.setStatus("APPROVED");
        order.setMaterialStatus(materialStatus);
        return order;
    }

    private static OrderMaterialRequirement requirement(long id, long materialId, String required) {
        return requirement(id, materialId, required, "0.0000");
    }

    private static OrderMaterialRequirement requirement(long id, long materialId, String required, String reserved) {
        OrderMaterialRequirement row = new OrderMaterialRequirement();
        row.setId(id);
        row.setOrderId(50L);
        row.setMaterialId(materialId);
        row.setRequiredQuantity(new BigDecimal(required));
        row.setReservedQuantity(new BigDecimal(reserved));
        row.setIssuedQuantity(new BigDecimal("0.0000"));
        return row;
    }

    private static InventoryBalance balance(long productId, String onHand, String reserved) {
        InventoryBalance balance = new InventoryBalance();
        balance.setProductId(productId);
        balance.setOnHandQuantity(new BigDecimal(onHand));
        balance.setReservedQuantity(new BigDecimal(reserved));
        return balance;
    }
}
