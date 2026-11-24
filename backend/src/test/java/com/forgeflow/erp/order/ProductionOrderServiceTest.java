package com.forgeflow.erp.order;

import com.forgeflow.erp.product.BomLine;
import com.forgeflow.erp.product.BomLineMapper;
import com.forgeflow.erp.product.Product;
import com.forgeflow.erp.product.ProductMapper;
import com.forgeflow.inventory.OrderMaterialRequirement;
import com.forgeflow.inventory.OrderMaterialRequirementMapper;
import com.forgeflow.platform.error.BusinessRuleException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductionOrderServiceTest {
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final ProductionOrderMapper orderMapper = mock(ProductionOrderMapper.class);
    private final BomLineMapper bomLineMapper = mock(BomLineMapper.class);
    private final OrderMaterialRequirementMapper requirementMapper = mock(OrderMaterialRequirementMapper.class);
    private final ProductionOrderService orderService = new ProductionOrderService(
            productMapper, orderMapper, bomLineMapper, requirementMapper);

    @Test
    void rejectsNonPositivePlannedQuantity() {
        assertThatThrownBy(() -> orderService.create(new ProductionOrderRequest(23L, 0), "planner"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("计划数量必须为正整数");
        verify(productMapper, never()).selectById(23L);
    }

    @Test
    void rejectsFinishedProductWithoutBom() {
        Product product = finishedProduct();
        when(productMapper.selectById(23L)).thenReturn(product);
        when(bomLineMapper.selectByProductId(23L)).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.create(new ProductionOrderRequest(23L, 120), "planner"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("BOM_REQUIRED_FOR_PRODUCTION"));
        verify(orderMapper, never()).insert(any(ProductionOrder.class));
    }

    @Test
    void createsDraftOrderAndSnapshotsBomRequirement() {
        when(productMapper.selectById(23L)).thenReturn(finishedProduct());
        when(bomLineMapper.selectByProductId(23L)).thenReturn(List.of(bomLine(8L, "0.1250")));
        doAnswer(invocation -> {
            ProductionOrder order = invocation.getArgument(0);
            order.setId(101L);
            return 1;
        }).when(orderMapper).insert(any(ProductionOrder.class));
        when(requirementMapper.insert(any(OrderMaterialRequirement.class))).thenReturn(1);

        ProductionOrderView result = orderService.create(new ProductionOrderRequest(23L, 120), "planner");

        ArgumentCaptor<OrderMaterialRequirement> requirement = ArgumentCaptor.forClass(OrderMaterialRequirement.class);
        verify(requirementMapper).insert(requirement.capture());
        assertThat(requirement.getValue().getOrderId()).isEqualTo(101L);
        assertThat(requirement.getValue().getMaterialId()).isEqualTo(8L);
        assertThat(requirement.getValue().getRequiredQuantity()).isEqualByComparingTo("15.0000");
        assertThat(result.id()).isEqualTo(101L);
        assertThat(result.productCode()).isEqualTo("FG-001");
        assertThat(result.plannedQuantity()).isEqualTo(120);
        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.materialStatus()).isEqualTo("NOT_READY");
    }

    private static Product finishedProduct() {
        Product product = new Product();
        product.setId(23L);
        product.setCode("FG-001");
        product.setProductType("FINISHED");
        product.setEnabled(true);
        return product;
    }

    private static BomLine bomLine(long materialId, String perUnit) {
        BomLine line = new BomLine();
        line.setId(materialId + 30);
        line.setProductId(23L);
        line.setMaterialId(materialId);
        line.setQuantity(new BigDecimal(perUnit));
        return line;
    }
}
