package com.forgeflow.mes;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("mes_production_report")
public class ProductionReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;
    private Integer goodQuantity;
    private Integer defectQuantity;
    private String operator;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Integer getGoodQuantity() { return goodQuantity; }
    public void setGoodQuantity(Integer goodQuantity) { this.goodQuantity = goodQuantity; }
    public Integer getDefectQuantity() { return defectQuantity; }
    public void setDefectQuantity(Integer defectQuantity) { this.defectQuantity = defectQuantity; }
    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
