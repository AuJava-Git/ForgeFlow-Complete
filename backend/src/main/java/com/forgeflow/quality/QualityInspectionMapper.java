package com.forgeflow.quality;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QualityInspectionMapper extends BaseMapper<QualityInspection> {
    @Select("SELECT * FROM quality_inspection WHERE report_id = #{reportId}")
    QualityInspection selectByReportId(@Param("reportId") long reportId);

    @Select("SELECT * FROM quality_inspection ORDER BY id DESC LIMIT 100")
    List<QualityInspection> selectRecent();
}
