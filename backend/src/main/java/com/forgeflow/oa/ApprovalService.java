package com.forgeflow.oa;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.forgeflow.erp.order.ProductionOrder;
import com.forgeflow.erp.order.ProductionOrderMapper;
import com.forgeflow.platform.error.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ApprovalService {
    private final ProductionOrderMapper orderMapper;
    private final ApprovalMapper approvalMapper;

    public ApprovalService(ProductionOrderMapper orderMapper, ApprovalMapper approvalMapper) {
        this.orderMapper = orderMapper;
        this.approvalMapper = approvalMapper;
    }

    @Transactional
    public void submit(long orderId, String actor) {
        int updated = orderMapper.update(null, Wrappers.<ProductionOrder>lambdaUpdate()
                .eq(ProductionOrder::getId, orderId)
                .eq(ProductionOrder::getStatus, "DRAFT")
                .set(ProductionOrder::getStatus, "PENDING_APPROVAL"));
        if (updated != 1) {
            throw BusinessRuleException.conflict("ORDER_NOT_DRAFT", "只有草稿订单可以提交审批");
        }
        appendAction(orderId, "SUBMIT", actor, null);
    }

    @Transactional
    public void decide(long orderId, boolean approved, String comment, String actor) {
        String nextStatus = approved ? "APPROVED" : "REJECTED";
        int updated = orderMapper.update(null, Wrappers.<ProductionOrder>lambdaUpdate()
                .eq(ProductionOrder::getId, orderId)
                .eq(ProductionOrder::getStatus, "PENDING_APPROVAL")
                .set(ProductionOrder::getStatus, nextStatus));
        if (updated != 1) {
            throw BusinessRuleException.conflict("ORDER_NOT_PENDING", "只有待审批订单可以作出决定");
        }
        appendAction(orderId, approved ? "APPROVE" : "REJECT", actor, comment);
    }

    public List<ApprovalOrderView> pending() {
        return orderMapper.selectList(Wrappers.<ProductionOrder>lambdaQuery()
                        .eq(ProductionOrder::getStatus, "PENDING_APPROVAL")
                        .orderByAsc(ProductionOrder::getId))
                .stream()
                .map(order -> new ApprovalOrderView(order.getId(), order.getOrderNo(), order.getProductId(),
                        order.getPlannedQuantity(), order.getStatus()))
                .toList();
    }

    public List<ApprovalHistoryView> history(long orderId) {
        return approvalMapper.selectList(Wrappers.<ApprovalAction>lambdaQuery()
                        .eq(ApprovalAction::getOrderId, orderId)
                        .orderByAsc(ApprovalAction::getId))
                .stream()
                .map(action -> new ApprovalHistoryView(action.getId(), action.getActionCode(), action.getActor(),
                        action.getComment(), action.getCreatedAt()))
                .toList();
    }

    private void appendAction(long orderId, String actionCode, String actor, String comment) {
        ApprovalAction action = new ApprovalAction();
        action.setOrderId(orderId);
        action.setActionCode(actionCode);
        action.setActor(actor);
        action.setComment(comment);
        approvalMapper.insert(action);
    }
}
