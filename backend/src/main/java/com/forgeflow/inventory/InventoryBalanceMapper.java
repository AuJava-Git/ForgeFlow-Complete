package com.forgeflow.inventory;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface InventoryBalanceMapper extends BaseMapper<InventoryBalance> {
    @Select("SELECT * FROM inv_stock_balance ORDER BY product_id")
    List<InventoryBalance> listAll();

    @Select("SELECT * FROM inv_stock_balance WHERE product_id = #{productId} FOR UPDATE")
    InventoryBalance lockByProductId(@Param("productId") long productId);

    @Insert("INSERT INTO inv_stock_balance(product_id, on_hand_quantity, reserved_quantity) VALUES(#{productId}, 0, 0) ON DUPLICATE KEY UPDATE product_id = VALUES(product_id)")
    int ensureBalance(@Param("productId") long productId);

    @Update("UPDATE inv_stock_balance SET on_hand_quantity = on_hand_quantity + #{quantity} WHERE product_id = #{productId}")
    int increaseOnHand(@Param("productId") long productId, @Param("quantity") BigDecimal quantity);

    @Update("UPDATE inv_stock_balance SET reserved_quantity = reserved_quantity + #{quantity} WHERE product_id = #{productId} AND on_hand_quantity - reserved_quantity >= #{quantity}")
    int reserveIfAvailable(@Param("productId") long productId, @Param("quantity") BigDecimal quantity);

    @Update("UPDATE inv_stock_balance SET on_hand_quantity = on_hand_quantity - #{quantity}, reserved_quantity = reserved_quantity - #{quantity} WHERE product_id = #{productId} AND on_hand_quantity >= #{quantity} AND reserved_quantity >= #{quantity}")
    int issueReserved(@Param("productId") long productId, @Param("quantity") BigDecimal quantity);
}
