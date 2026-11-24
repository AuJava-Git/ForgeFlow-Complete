package com.forgeflow.erp.order;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("erp_production_order")
public class ProductionOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private Long productId;
    private Integer plannedQuantity;
    private Integer goodQuantity;
    private Integer defectQuantity;
    private String status;
    private String materialStatus;
    private String createdBy;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Integer getPlannedQuantity() { return plannedQuantity; }
    public void setPlannedQuantity(Integer plannedQuantity) { this.plannedQuantity = plannedQuantity; }
    public Integer getGoodQuantity() { return goodQuantity; }
    public void setGoodQuantity(Integer goodQuantity) { this.goodQuantity = goodQuantity; }
    public Integer getDefectQuantity() { return defectQuantity; }
    public void setDefectQuantity(Integer defectQuantity) { this.defectQuantity = defectQuantity; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMaterialStatus() { return materialStatus; }
    public void setMaterialStatus(String materialStatus) { this.materialStatus = materialStatus; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
