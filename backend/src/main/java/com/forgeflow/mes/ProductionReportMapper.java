package com.forgeflow.mes;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductionReportMapper extends BaseMapper<ProductionReport> {
    @Select("SELECT r.* FROM mes_production_report r WHERE r.good_quantity > 0 AND NOT EXISTS "
            + "(SELECT 1 FROM quality_inspection q WHERE q.report_id = r.id) ORDER BY r.reported_at, r.id")
    List<ProductionReport> selectPendingQualityInspection();
}
