package com.forgeflow.erp.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductionOrderMapper extends BaseMapper<ProductionOrder> {
    @Update("""
            UPDATE erp_production_order
            SET status = CASE
                    WHEN good_quantity + defect_quantity + #{goodQuantity} + #{defectQuantity} = planned_quantity THEN 'COMPLETED'
                    ELSE status
                END,
                good_quantity = good_quantity + #{goodQuantity},
                defect_quantity = defect_quantity + #{defectQuantity}
            WHERE id = #{orderId}
              AND status = 'IN_PROGRESS'
              AND good_quantity + defect_quantity + #{goodQuantity} + #{defectQuantity} <= planned_quantity
            """)
    int updateProgressIfWithinPlan(@Param("orderId") long orderId,
                                   @Param("goodQuantity") int goodQuantity,
                                   @Param("defectQuantity") int defectQuantity);

    @Update("UPDATE erp_production_order SET material_status = #{materialStatus} WHERE id = #{orderId} AND status = 'APPROVED' AND material_status = #{expectedStatus}")
    int updateMaterialStatus(@Param("orderId") long orderId, @Param("expectedStatus") String expectedStatus,
                             @Param("materialStatus") String materialStatus);

    @Update("UPDATE erp_production_order SET status = 'IN_PROGRESS' WHERE id = #{orderId} AND status = 'APPROVED' AND material_status = 'ISSUED'")
    int startIfMaterialsIssued(@Param("orderId") long orderId);
}
