package com.forgeflow.purchasing;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface PurchaseOrderLineMapper extends BaseMapper<PurchaseOrderLine> {
    @Select("SELECT * FROM purchasing_order_line WHERE purchase_order_id = #{purchaseOrderId} ORDER BY material_id")
    List<PurchaseOrderLine> selectByPurchaseOrderId(@Param("purchaseOrderId") long purchaseOrderId);

    @Update("UPDATE purchasing_order_line SET received_quantity = received_quantity + #{quantity} WHERE id = #{id} AND received_quantity + #{quantity} <= ordered_quantity")
    int addReceivedIfWithinOrder(@Param("id") long id, @Param("quantity") BigDecimal quantity);
}
