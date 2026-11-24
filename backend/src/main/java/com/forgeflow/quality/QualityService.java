package com.forgeflow.quality;

import com.forgeflow.erp.order.ProductionOrder;
import com.forgeflow.erp.order.ProductionOrderMapper;
import com.forgeflow.inventory.InventoryService;
import com.forgeflow.mes.ProductionReport;
import com.forgeflow.mes.ProductionReportMapper;
import com.forgeflow.platform.error.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class QualityService {
    private final ProductionReportMapper reportMapper;
    private final QualityInspectionMapper inspectionMapper;
    private final ProductionOrderMapper orderMapper;
    private final InventoryService inventoryService;

    public QualityService(ProductionReportMapper reportMapper, QualityInspectionMapper inspectionMapper,
                          ProductionOrderMapper orderMapper, InventoryService inventoryService) {
        this.reportMapper = reportMapper;
        this.inspectionMapper = inspectionMapper;
        this.orderMapper = orderMapper;
        this.inventoryService = inventoryService;
    }

    @Transactional(readOnly = true)
    public List<QualityPendingView> pending() {
        return reportMapper.selectPendingQualityInspection().stream().map(report -> {
            ProductionOrder order = orderMapper.selectById(report.getOrderId());
            return new QualityPendingView(report.getId(), report.getOrderId(), order == null ? "" : order.getOrderNo(),
                    report.getGoodQuantity(), report.getDefectQuantity(), report.getOperator(), report.getRemark());
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<QualityInspectionView> recent() {
        return inspectionMapper.selectRecent().stream().map(inspection -> view(inspection,
                inventoryService.hasFinishedGoodsReceipt(inspection.getId()))).toList();
    }

    @Transactional
    public QualityInspectionView inspect(long reportId, QualityInspectionRequest request, String actor) {
        ProductionReport report = reportMapper.selectById(reportId);
        if (report == null) throw BusinessRuleException.notFound("PRODUCTION_REPORT_NOT_FOUND", "MES 报工记录不存在");
        if (report.getGoodQuantity() == null || report.getGoodQuantity() <= 0) {
            throw BusinessRuleException.conflict("NO_INSPECTABLE_GOOD_QUANTITY", "该报工没有合格产出可供质量检验");
        }
        if (inspectionMapper.selectByReportId(reportId) != null) {
            throw BusinessRuleException.conflict("REPORT_ALREADY_INSPECTED", "该报工批次已经检验，不能重复检验");
        }
        if (request == null || request.passedQuantity() == null || request.rejectedQuantity() == null
                || request.passedQuantity() < 0 || request.rejectedQuantity() < 0
                || request.passedQuantity() + request.rejectedQuantity() != report.getGoodQuantity()) {
            throw BusinessRuleException.badRequest("QUALITY_QUANTITY_MISMATCH", "质检通过数与拒收数之和必须等于 MES 合格报工数");
        }
        QualityInspection inspection = new QualityInspection();
        inspection.setReportId(reportId);
        inspection.setOrderId(report.getOrderId());
        inspection.setPassedQuantity(request.passedQuantity());
        inspection.setRejectedQuantity(request.rejectedQuantity());
        inspection.setResult(result(request.passedQuantity(), request.rejectedQuantity()));
        inspection.setInspector(actor);
        inspection.setRemark(request.remark());
        if (inspectionMapper.insert(inspection) != 1) throw new IllegalStateException("质量检验记录写入失败");
        return view(inspection, false);
    }

    @Transactional
    public void receiveToFinishedGoods(long inspectionId, String actor) {
        QualityInspection inspection = inspectionMapper.selectById(inspectionId);
        if (inspection == null) throw BusinessRuleException.notFound("QUALITY_INSPECTION_NOT_FOUND", "质量检验记录不存在");
        if (inspection.getPassedQuantity() == null || inspection.getPassedQuantity() <= 0) {
            throw BusinessRuleException.conflict("NO_PASSED_QUANTITY", "没有质检通过数量可入成品库存");
        }
        if (inventoryService.hasFinishedGoodsReceipt(inspectionId)) {
            throw BusinessRuleException.conflict("INSPECTION_ALREADY_RECEIVED", "该质检记录已经入库");
        }
        ProductionOrder order = orderMapper.selectById(inspection.getOrderId());
        if (order == null) throw BusinessRuleException.notFound("PRODUCTION_ORDER_NOT_FOUND", "质检记录关联的生产订单不存在");
        BigDecimal passed = BigDecimal.valueOf(inspection.getPassedQuantity()).setScale(4);
        inventoryService.receiveFinishedGoods(inspectionId, order.getProductId(), passed, actor);
    }

    private static QualityInspectionView view(QualityInspection inspection, boolean received) {
        return new QualityInspectionView(inspection.getId(), inspection.getReportId(), inspection.getOrderId(),
                inspection.getPassedQuantity(), inspection.getRejectedQuantity(), inspection.getResult(),
                inspection.getInspector(), received);
    }

    private static String result(int passed, int rejected) {
        if (rejected == 0) return "PASS";
        if (passed == 0) return "REJECTED";
        return "PARTIAL";
    }
}
