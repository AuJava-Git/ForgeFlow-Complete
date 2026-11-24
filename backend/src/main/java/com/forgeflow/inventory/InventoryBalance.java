package com.forgeflow.inventory;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;

@TableName("inv_stock_balance")
public class InventoryBalance {
    @TableId
    private Long productId;
    private BigDecimal onHandQuantity;
    private BigDecimal reservedQuantity;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public BigDecimal getOnHandQuantity() { return onHandQuantity; }
    public void setOnHandQuantity(BigDecimal onHandQuantity) { this.onHandQuantity = onHandQuantity; }
    public BigDecimal getReservedQuantity() { return reservedQuantity; }
    public void setReservedQuantity(BigDecimal reservedQuantity) { this.reservedQuantity = reservedQuantity; }
}
