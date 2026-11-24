package com.forgeflow.quality;

import com.forgeflow.erp.order.ProductionOrder;
import com.forgeflow.erp.order.ProductionOrderMapper;
import com.forgeflow.inventory.InventoryService;
import com.forgeflow.mes.ProductionReport;
import com.forgeflow.mes.ProductionReportMapper;
import com.forgeflow.platform.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QualityServiceTest {
    private final ProductionReportMapper reportMapper = mock(ProductionReportMapper.class);
    private final QualityInspectionMapper inspectionMapper = mock(QualityInspectionMapper.class);
    private final ProductionOrderMapper orderMapper = mock(ProductionOrderMapper.class);
    private final InventoryService inventoryService = mock(InventoryService.class);
    private final QualityService service = new QualityService(reportMapper, inspectionMapper, orderMapper, inventoryService);

    @Test
    void rejectsInspectionWhenAcceptedAndRejectedDoNotMatchMesGoodQuantity() {
        ProductionReport report = report(70L, 50L, 3);
        when(reportMapper.selectById(70L)).thenReturn(report);
        when(inspectionMapper.selectByReportId(70L)).thenReturn(null);

        assertThatThrownBy(() -> service.inspect(70L, new QualityInspectionRequest(2, 0, "数量不符"), "qa"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("QUALITY_QUANTITY_MISMATCH"));
        verify(inspectionMapper, never()).insert(any(QualityInspection.class));
    }

    @Test
    void inspectionStoresPassAndRejectCountsAndOnlyPassQuantityCanEnterFinishedGoodsStock() {
        ProductionReport report = report(70L, 50L, 3);
        when(reportMapper.selectById(70L)).thenReturn(report);
        when(inspectionMapper.selectByReportId(70L)).thenReturn(null);
        when(inspectionMapper.insert(any(QualityInspection.class))).thenAnswer(invocation -> {
            QualityInspection inspection = invocation.getArgument(0);
            inspection.setId(90L);
            return 1;
        });
        QualityInspection inspection = new QualityInspection();
        inspection.setId(90L);
        inspection.setReportId(70L);
        inspection.setOrderId(50L);
        inspection.setPassedQuantity(2);
        inspection.setRejectedQuantity(1);
        inspection.setResult("PARTIAL");
        when(inspectionMapper.selectById(90L)).thenReturn(inspection);
        when(inventoryService.hasFinishedGoodsReceipt(90L)).thenReturn(false);
        ProductionOrder order = new ProductionOrder();
        order.setId(50L);
        order.setProductId(500L);
        when(orderMapper.selectById(50L)).thenReturn(order);

        QualityInspectionView result = service.inspect(70L, new QualityInspectionRequest(2, 1, "抽检"), "qa");
        service.receiveToFinishedGoods(90L, "qa");

        assertThat(result.result()).isEqualTo("PARTIAL");
        assertThat(result.passedQuantity()).isEqualTo(2);
        verify(inventoryService).receiveFinishedGoods(90L, 500L, new BigDecimal("2.0000"), "qa");
    }

    private static ProductionReport report(long id, long orderId, int goodQuantity) {
        ProductionReport report = new ProductionReport();
        report.setId(id);
        report.setOrderId(orderId);
        report.setGoodQuantity(goodQuantity);
        report.setDefectQuantity(2);
        return report;
    }
}
