package com.forgeflow.purchasing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface PurchaseOrderMapper extends BaseMapper<PurchaseOrder> {
    @Select("SELECT * FROM purchasing_order WHERE production_order_id = #{productionOrderId}")
    PurchaseOrder selectByProductionOrderId(@Param("productionOrderId") long productionOrderId);

    @Update("UPDATE purchasing_order SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") long id, @Param("status") String status);
}
