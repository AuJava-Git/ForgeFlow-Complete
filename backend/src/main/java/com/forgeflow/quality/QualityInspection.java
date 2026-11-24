package com.forgeflow.quality;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("quality_inspection")
public class QualityInspection {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private Long orderId;
    private Integer passedQuantity;
    private Integer rejectedQuantity;
    private String result;
    private String inspector;
    private String remark;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getReportId() { return reportId; }
    public void setReportId(Long reportId) { this.reportId = reportId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public Integer getPassedQuantity() { return passedQuantity; }
    public void setPassedQuantity(Integer passedQuantity) { this.passedQuantity = passedQuantity; }
    public Integer getRejectedQuantity() { return rejectedQuantity; }
    public void setRejectedQuantity(Integer rejectedQuantity) { this.rejectedQuantity = rejectedQuantity; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public String getInspector() { return inspector; }
    public void setInspector(String inspector) { this.inspector = inspector; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
