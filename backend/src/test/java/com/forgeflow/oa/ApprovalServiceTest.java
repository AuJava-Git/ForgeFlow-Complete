package com.forgeflow.oa;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.forgeflow.erp.order.ProductionOrder;
import com.forgeflow.erp.order.ProductionOrderMapper;
import com.forgeflow.platform.error.BusinessRuleException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApprovalServiceTest {
    private final ProductionOrderMapper orderMapper = mock(ProductionOrderMapper.class);
    private final ApprovalMapper approvalMapper = mock(ApprovalMapper.class);
    private final ApprovalService approvalService = new ApprovalService(orderMapper, approvalMapper);

    @BeforeAll
    static void initializeOrderTableMetadata() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "approval-test");
        assistant.setCurrentNamespace(ProductionOrderMapper.class.getName());
        TableInfoHelper.initTableInfo(assistant, ProductionOrder.class);
    }

    @Test
    void approvesOnlyPendingOrderAndAppendsHistory() {
        when(orderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        approvalService.decide(42L, true, "物料与工期确认", "approver");

        ArgumentCaptor<ApprovalAction> action = ArgumentCaptor.forClass(ApprovalAction.class);
        verify(approvalMapper).insert(action.capture());
        org.assertj.core.api.Assertions.assertThat(action.getValue().getActionCode()).isEqualTo("APPROVE");
        org.assertj.core.api.Assertions.assertThat(action.getValue().getActor()).isEqualTo("approver");
        org.assertj.core.api.Assertions.assertThat(action.getValue().getComment()).isEqualTo("物料与工期确认");
    }

    @Test
    void rejectsDecisionForNonPendingOrderWithoutHistory() {
        when(orderMapper.update(isNull(), any(Wrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> approvalService.decide(42L, false, "请补充计划", "approver"))
                .isInstanceOf(BusinessRuleException.class);
        verify(approvalMapper, never()).insert(any(ApprovalAction.class));
    }

    @Test
    void submitsDraftOrderAndAppendsSubmitHistory() {
        when(orderMapper.update(isNull(), any(Wrapper.class))).thenReturn(1);

        approvalService.submit(42L, "planner");

        ArgumentCaptor<ApprovalAction> action = ArgumentCaptor.forClass(ApprovalAction.class);
        verify(approvalMapper).insert(action.capture());
        org.assertj.core.api.Assertions.assertThat(action.getValue().getActionCode()).isEqualTo("SUBMIT");
        org.assertj.core.api.Assertions.assertThat(action.getValue().getActor()).isEqualTo("planner");
    }
}
