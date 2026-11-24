package com.forgeflow.dashboard;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface DashboardMapper {
    @Select("""
            SELECT COUNT(*) AS total_orders,
                   COALESCE(SUM(CASE WHEN status = 'PENDING_APPROVAL' THEN 1 ELSE 0 END), 0) AS pending_approval,
                   COALESCE(SUM(CASE WHEN status = 'IN_PROGRESS' THEN 1 ELSE 0 END), 0) AS in_progress,
                   COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completed,
                   COALESCE(SUM(good_quantity), 0) AS good_quantity,
                   COALESCE(SUM(defect_quantity), 0) AS defect_quantity
            FROM erp_production_order
            """)
    Map<String, Object> summary();
}
