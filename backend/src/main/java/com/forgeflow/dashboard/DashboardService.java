package com.forgeflow.dashboard;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DashboardService {
    private final DashboardMapper dashboardMapper;

    public DashboardService(DashboardMapper dashboardMapper) {
        this.dashboardMapper = dashboardMapper;
    }

    public DashboardSummary summary() {
        Map<String, Object> row = dashboardMapper.summary();
        if (row == null) {
            return new DashboardSummary(0, 0, 0, 0, 0, 0);
        }
        return new DashboardSummary(value(row, "total_orders"), value(row, "pending_approval"),
                value(row, "in_progress"), value(row, "completed"), value(row, "good_quantity"),
                value(row, "defect_quantity"));
    }

    private static long value(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value instanceof Number number ? number.longValue() : 0;
    }
}
