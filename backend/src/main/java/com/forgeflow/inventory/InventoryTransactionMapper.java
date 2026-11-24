package com.forgeflow.inventory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface InventoryTransactionMapper extends BaseMapper<InventoryTransaction> {
    @Select("SELECT COUNT(*) FROM inv_finished_goods_receipt WHERE inspection_id = #{inspectionId}")
    int countFinishedGoodsReceipt(@Param("inspectionId") long inspectionId);

    @Insert("INSERT INTO inv_finished_goods_receipt(inspection_id, product_id, quantity, received_by) VALUES(#{inspectionId}, #{productId}, #{quantity}, #{actor})")
    int insertFinishedGoodsReceipt(@Param("inspectionId") long inspectionId, @Param("productId") long productId,
                                   @Param("quantity") java.math.BigDecimal quantity, @Param("actor") String actor);
}
