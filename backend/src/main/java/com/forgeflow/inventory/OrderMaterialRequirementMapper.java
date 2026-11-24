package com.forgeflow.inventory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface OrderMaterialRequirementMapper extends BaseMapper<OrderMaterialRequirement> {
    @Select("SELECT * FROM erp_order_material_requirement WHERE order_id = #{orderId} ORDER BY material_id")
    List<OrderMaterialRequirement> selectByOrderId(@Param("orderId") long orderId);

    @Update("UPDATE erp_order_material_requirement SET reserved_quantity = #{quantity} WHERE id = #{id}")
    int updateReserved(@Param("id") long id, @Param("quantity") BigDecimal quantity);

    @Update("UPDATE erp_order_material_requirement SET issued_quantity = #{quantity} WHERE id = #{id}")
    int updateIssued(@Param("id") long id, @Param("quantity") BigDecimal quantity);
}
