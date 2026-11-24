package com.forgeflow.dashboard;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {
    private final DashboardMapper dashboardMapper = mock(DashboardMapper.class);
    private final DashboardService dashboardService = new DashboardService(dashboardMapper);

    @Test
    void summarizesOrderStatesAndReportTotals() {
        when(dashboardMapper.summary()).thenReturn(Map.of(
                "total_orders", 8L,
                "pending_approval", 2L,
                "in_progress", 3L,
                "completed", 1L,
                "good_quantity", new BigDecimal("140"),
                "defect_quantity", new BigDecimal("7")));

        DashboardSummary summary = dashboardService.summary();

        assertThat(summary.totalOrders()).isEqualTo(8);
        assertThat(summary.pendingApproval()).isEqualTo(2);
        assertThat(summary.inProgress()).isEqualTo(3);
        assertThat(summary.completed()).isEqualTo(1);
        assertThat(summary.goodQuantity()).isEqualTo(140);
        assertThat(summary.defectQuantity()).isEqualTo(7);
    }

    @Test
    void returnsZeroesWhenDatabaseAggregationIsEmpty() {
        when(dashboardMapper.summary()).thenReturn(Map.of());

        assertThat(dashboardService.summary()).isEqualTo(new DashboardSummary(0, 0, 0, 0, 0, 0));
    }
}
