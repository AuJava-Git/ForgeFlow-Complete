package com.forgeflow.mes;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.forgeflow.erp.order.ProductionOrder;
import com.forgeflow.erp.order.ProductionOrderMapper;
import com.forgeflow.platform.error.BusinessRuleException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MesServiceTest {
    private final ProductionOrderMapper orderMapper = mock(ProductionOrderMapper.class);
    private final ProductionReportMapper reportMapper = mock(ProductionReportMapper.class);
    private final MesService mesService = new MesService(orderMapper, reportMapper);

    @BeforeAll
    static void initializeOrderTableMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "mes-test");
        assistant.setCurrentNamespace(ProductionOrderMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, ProductionOrder.class);
    }

    @Test
    void startRejectsOrderUntilAllMaterialsAreIssued() {
        when(orderMapper.startIfMaterialsIssued(11L)).thenReturn(0);

        assertThatThrownBy(() -> mesService.start(11L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("只有已获批且完成领料的订单可以开工");
    }

    @Test
    void workOrdersHideApprovedOrdersUntilMaterialsAreIssued() {
        ProductionOrder ready = order(21L, 10, 0, 0);
        ready.setOrderNo("READY");
        ready.setStatus("APPROVED");
        ready.setMaterialStatus("ISSUED");
        ProductionOrder waitingForMaterials = order(22L, 10, 0, 0);
        waitingForMaterials.setOrderNo("WAITING");
        waitingForMaterials.setStatus("APPROVED");
        waitingForMaterials.setMaterialStatus("NOT_READY");
        ProductionOrder inProgress = order(23L, 10, 2, 0);
        inProgress.setOrderNo("IN-PROGRESS");
        inProgress.setMaterialStatus("ISSUED");
        when(orderMapper.selectList(any())).thenReturn(java.util.List.of(ready, waitingForMaterials, inProgress));

        assertThat(mesService.workOrders()).extracting(MesWorkOrderView::id).containsExactly(21L, 23L);
    }

    @Test
    void rejectsReportWithNoPositiveQuantity() {
        assertThatThrownBy(() -> mesService.report(11L, new ProductionReportRequest(0, 0, ""), "operator"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("合格数和不合格数至少有一项必须大于零");
        verify(orderMapper, never()).selectById(11L);
    }

    @Test
    void rejectsReportAboveRemainingPlan() {
        ProductionOrder order = order(11L, 5, 3, 1);
        when(orderMapper.selectById(11L)).thenReturn(order);

        assertThatThrownBy(() -> mesService.report(11L, new ProductionReportRequest(2, 0, "超量"), "operator"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("报工数量不能超过剩余计划数量");
        verify(orderMapper, never()).updateProgressIfWithinPlan(anyLong(), anyInt(), anyInt());
        verify(reportMapper, never()).insert(any(ProductionReport.class));
    }

    @Test
    void completesOrderWhenReportedTotalReachesPlan() {
        ProductionOrder order = order(11L, 4, 2, 1);
        when(orderMapper.selectById(11L)).thenReturn(order);
        when(orderMapper.updateProgressIfWithinPlan(11L, 1, 0)).thenReturn(1);
        when(reportMapper.insert(any(ProductionReport.class))).thenAnswer(invocation -> {
            ProductionReport report = invocation.getArgument(0);
            report.setId(71L);
            return 1;
        });

        ProductionReportView result = mesService.report(11L, new ProductionReportRequest(1, 0, "最后一批"), "operator");

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.goodQuantityTotal()).isEqualTo(3);
        assertThat(result.defectQuantityTotal()).isEqualTo(1);
        assertThat(result.id()).isEqualTo(71L);
        verify(orderMapper).updateProgressIfWithinPlan(11L, 1, 0);
        verify(reportMapper).insert(any(ProductionReport.class));
    }

    private static ProductionOrder order(long id, int planned, int good, int defect) {
        ProductionOrder order = new ProductionOrder();
        order.setId(id);
        order.setPlannedQuantity(planned);
        order.setGoodQuantity(good);
        order.setDefectQuantity(defect);
        order.setStatus("IN_PROGRESS");
        return order;
    }
}
