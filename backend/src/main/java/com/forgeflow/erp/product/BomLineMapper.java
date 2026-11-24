package com.forgeflow.erp.product;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BomLineMapper extends BaseMapper<BomLine> {
    @Select("SELECT * FROM erp_bom_line WHERE product_id = #{productId} ORDER BY material_id")
    List<BomLine> selectByProductId(@Param("productId") long productId);
}
