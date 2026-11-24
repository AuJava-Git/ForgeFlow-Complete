package com.forgeflow.mes;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.forgeflow.erp.order.ProductionOrder;
import com.forgeflow.erp.order.ProductionOrderMapper;
import com.forgeflow.platform.error.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MesService {
    private final ProductionOrderMapper orderMapper;
    private final ProductionReportMapper reportMapper;

    public MesService(ProductionOrderMapper orderMapper, ProductionReportMapper reportMapper) {
        this.orderMapper = orderMapper;
        this.reportMapper = reportMapper;
    }

    public List<MesWorkOrderView> workOrders() {
        return orderMapper.selectList(Wrappers.<ProductionOrder>lambdaQuery()
                        .in(ProductionOrder::getStatus, "APPROVED", "IN_PROGRESS")
                        .orderByAsc(ProductionOrder::getId))
                .stream()
                .filter(order -> "IN_PROGRESS".equals(order.getStatus())
                        || ("APPROVED".equals(order.getStatus()) && "ISSUED".equals(order.getMaterialStatus())))
                .map(order -> new MesWorkOrderView(order.getId(), order.getOrderNo(), order.getProductId(),
                        order.getPlannedQuantity(), order.getGoodQuantity(), order.getDefectQuantity(), order.getStatus(),
                        order.getMaterialStatus()))
                .toList();
    }

    @Transactional
    public void start(long orderId) {
        int updated = orderMapper.startIfMaterialsIssued(orderId);
        if (updated != 1) {
            throw BusinessRuleException.conflict("MATERIALS_NOT_ISSUED", "只有已获批且完成领料的订单可以开工");
        }
    }

    @Transactional
    public ProductionReportView report(long orderId, ProductionReportRequest request, String actor) {
        long reportedQuantity = (long) request.goodQuantity() + request.defectQuantity();
        if (request.goodQuantity() < 0 || request.defectQuantity() < 0 || reportedQuantity <= 0) {
            throw BusinessRuleException.badRequest("INVALID_REPORT_QUANTITY", "合格数和不合格数至少有一项必须大于零");
        }
        ProductionOrder order = orderMapper.selectById(orderId);
        if (order == null) {
            throw BusinessRuleException.notFound("PRODUCTION_ORDER_NOT_FOUND", "生产订单不存在");
        }
        if (!"IN_PROGRESS".equals(order.getStatus())) {
            throw BusinessRuleException.conflict("ORDER_NOT_IN_PROGRESS", "只有生产中的订单可以报工");
        }
        int previousGood = order.getGoodQuantity() == null ? 0 : order.getGoodQuantity();
        int previousDefect = order.getDefectQuantity() == null ? 0 : order.getDefectQuantity();
        long newTotal = (long) previousGood + previousDefect + reportedQuantity;
        if (newTotal > order.getPlannedQuantity()) {
            throw BusinessRuleException.conflict("REPORT_EXCEEDS_REMAINING_PLAN", "报工数量不能超过剩余计划数量");
        }
        int updated = orderMapper.updateProgressIfWithinPlan(orderId, request.goodQuantity(), request.defectQuantity());
        if (updated != 1) {
            throw BusinessRuleException.conflict("REPORT_EXCEEDS_REMAINING_PLAN", "订单进度已变化，请刷新后重新报工");
        }

        ProductionReport report = new ProductionReport();
        report.setOrderId(orderId);
        report.setGoodQuantity(request.goodQuantity());
        report.setDefectQuantity(request.defectQuantity());
        report.setOperator(actor);
        report.setRemark(request.remark());
        if (reportMapper.insert(report) != 1) {
            throw new IllegalStateException("MES报工记录写入失败");
        }

        int totalGood = previousGood + request.goodQuantity();
        int totalDefect = previousDefect + request.defectQuantity();
        String status = newTotal == order.getPlannedQuantity() ? "COMPLETED" : "IN_PROGRESS";
        return new ProductionReportView(report.getId(), orderId, request.goodQuantity(), request.defectQuantity(),
                (int) reportedQuantity, totalGood, totalDefect, status);
    }
}
