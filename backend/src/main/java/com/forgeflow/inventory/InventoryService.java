package com.forgeflow.inventory;

import com.forgeflow.erp.order.ProductionOrder;
import com.forgeflow.erp.order.ProductionOrderMapper;
import com.forgeflow.erp.product.Product;
import com.forgeflow.erp.product.ProductMapper;
import com.forgeflow.platform.error.BusinessRuleException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.forgeflow.inventory.InventoryDtos.InventoryBalanceView;
import static com.forgeflow.inventory.InventoryDtos.InventoryReceiptRequest;
import static com.forgeflow.inventory.InventoryDtos.MaterialRequirementView;

@Service
public class InventoryService {
    private static final BigDecimal ZERO = new BigDecimal("0.0000");

    private final ProductMapper productMapper;
    private final ProductionOrderMapper orderMapper;
    private final OrderMaterialRequirementMapper requirementMapper;
    private final InventoryBalanceMapper balanceMapper;
    private final InventoryTransactionMapper transactionMapper;

    public InventoryService(ProductMapper productMapper, ProductionOrderMapper orderMapper,
                            OrderMaterialRequirementMapper requirementMapper, InventoryBalanceMapper balanceMapper,
                            InventoryTransactionMapper transactionMapper) {
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.requirementMapper = requirementMapper;
        this.balanceMapper = balanceMapper;
        this.transactionMapper = transactionMapper;
    }

    @Transactional(readOnly = true)
    public List<InventoryBalanceView> balances() {
        return balanceMapper.listAll().stream().map(balance -> {
            Product product = productMapper.selectById(balance.getProductId());
            if (product == null) return null;
            BigDecimal onHand = value(balance.getOnHandQuantity());
            BigDecimal reserved = value(balance.getReservedQuantity());
            return new InventoryBalanceView(product.getId(), product.getCode(), product.getName(),
                    product.getProductType(), product.getUnit(), onHand, reserved, onHand.subtract(reserved));
        }).filter(row -> row != null).toList();
    }

    @Transactional
    public void receiveOpeningStock(InventoryReceiptRequest request, String actor) {
        Product product = requireEnabledProduct(request.productId());
        if (!"MATERIAL".equals(product.getProductType())) {
            throw BusinessRuleException.badRequest("RAW_MATERIAL_REQUIRED", "期初收货只能登记原材料");
        }
        requirePositive(request.quantity());
        if (request.reason() == null || request.reason().isBlank()) {
            throw BusinessRuleException.badRequest("RECEIPT_REASON_REQUIRED", "收货原因不能为空");
        }
        receiveProduct(product.getId(), request.quantity(), "OPENING_RECEIPT", "MANUAL", null,
                request.reason(), actor);
    }

    @Transactional(readOnly = true)
    public List<MaterialRequirementView> requirements(long orderId) {
        requireOrder(orderId);
        List<OrderMaterialRequirement> requirements = requireSnapshot(orderId);
        return requirements.stream().map(requirement -> {
            Product material = productMapper.selectById(requirement.getMaterialId());
            InventoryBalance balance = balanceMapper.selectById(requirement.getMaterialId());
            BigDecimal available = balance == null ? ZERO : value(balance.getOnHandQuantity())
                    .subtract(value(balance.getReservedQuantity()));
            BigDecimal shortage = maxZero(requirement.getRequiredQuantity().subtract(available));
            return new MaterialRequirementView(requirement.getMaterialId(),
                    material == null ? "" : material.getCode(), material == null ? "" : material.getName(),
                    material == null ? "" : material.getUnit(), requirement.getRequiredQuantity(),
                    value(requirement.getReservedQuantity()), value(requirement.getIssuedQuantity()), available, shortage);
        }).toList();
    }

    @Transactional
    public void reserve(long orderId, String actor) {
        ProductionOrder order = requireOrder(orderId);
        requireApproved(order);
        if (!"NOT_READY".equals(order.getMaterialStatus())) {
            throw BusinessRuleException.conflict("MATERIALS_ALREADY_RESERVED", "订单物料已经预留或发料，不能重复备料");
        }
        List<OrderMaterialRequirement> requirements = requireSnapshot(orderId);
        Map<Long, InventoryBalance> lockedBalances = new LinkedHashMap<>();
        for (OrderMaterialRequirement requirement : requirements) {
            balanceMapper.ensureBalance(requirement.getMaterialId());
            InventoryBalance balance = balanceMapper.lockByProductId(requirement.getMaterialId());
            if (balance == null) balance = emptyBalance(requirement.getMaterialId());
            BigDecimal available = value(balance.getOnHandQuantity()).subtract(value(balance.getReservedQuantity()));
            if (available.compareTo(requirement.getRequiredQuantity()) < 0) {
                Product material = productMapper.selectById(requirement.getMaterialId());
                String code = material == null ? String.valueOf(requirement.getMaterialId()) : material.getCode();
                throw BusinessRuleException.conflict("INSUFFICIENT_STOCK",
                        "物料 " + code + " 库存不足，需 " + requirement.getRequiredQuantity() + "，可用 " + available);
            }
            lockedBalances.put(requirement.getMaterialId(), balance);
        }

        for (OrderMaterialRequirement requirement : requirements) {
            BigDecimal quantity = requirement.getRequiredQuantity();
            if (balanceMapper.reserveIfAvailable(requirement.getMaterialId(), quantity) != 1
                    || requirementMapper.updateReserved(requirement.getId(), quantity) != 1) {
                throw BusinessRuleException.conflict("STOCK_CHANGED", "库存已变化，备料未完成，请刷新后重试");
            }
            recordMovement(requirement.getMaterialId(), "RESERVE", quantity, "PRODUCTION_ORDER", orderId,
                    "生产订单备料预留", actor);
        }
        if (orderMapper.updateMaterialStatus(orderId, "NOT_READY", "RESERVED") != 1) {
            throw BusinessRuleException.conflict("MATERIAL_STATUS_CHANGED", "订单物料状态已变化，请刷新后重试");
        }
    }

    @Transactional
    public void issue(long orderId, String actor) {
        ProductionOrder order = requireOrder(orderId);
        requireApproved(order);
        if (!"RESERVED".equals(order.getMaterialStatus())) {
            throw BusinessRuleException.conflict("MATERIALS_NOT_RESERVED", "必须先完成整单备料才能领料");
        }
        List<OrderMaterialRequirement> requirements = requireSnapshot(orderId);
        for (OrderMaterialRequirement requirement : requirements) {
            if (value(requirement.getReservedQuantity()).compareTo(requirement.getRequiredQuantity()) != 0
                    || value(requirement.getIssuedQuantity()).signum() != 0) {
                throw BusinessRuleException.conflict("MATERIAL_RESERVATION_INCOMPLETE", "物料预留状态不完整，不能发料");
            }
            balanceMapper.ensureBalance(requirement.getMaterialId());
            InventoryBalance balance = balanceMapper.lockByProductId(requirement.getMaterialId());
            if (balance == null || value(balance.getReservedQuantity()).compareTo(requirement.getRequiredQuantity()) < 0
                    || value(balance.getOnHandQuantity()).compareTo(requirement.getRequiredQuantity()) < 0) {
                throw BusinessRuleException.conflict("RESERVED_STOCK_CHANGED", "已预留库存发生变化，不能发料");
            }
        }
        for (OrderMaterialRequirement requirement : requirements) {
            BigDecimal quantity = requirement.getRequiredQuantity();
            if (balanceMapper.issueReserved(requirement.getMaterialId(), quantity) != 1
                    || requirementMapper.updateIssued(requirement.getId(), quantity) != 1) {
                throw BusinessRuleException.conflict("ISSUE_FAILED", "领料未完成，库存状态已变化");
            }
            recordMovement(requirement.getMaterialId(), "ISSUE", quantity, "PRODUCTION_ORDER", orderId,
                    "生产订单领料", actor);
        }
        if (orderMapper.updateMaterialStatus(orderId, "RESERVED", "ISSUED") != 1) {
            throw BusinessRuleException.conflict("MATERIAL_STATUS_CHANGED", "订单物料状态已变化，请刷新后重试");
        }
    }

    @Transactional
    public void receivePurchasedMaterial(long purchaseOrderId, long materialId, BigDecimal quantity, String actor) {
        Product material = requireEnabledProduct(materialId);
        if (!"MATERIAL".equals(material.getProductType())) {
            throw BusinessRuleException.badRequest("RAW_MATERIAL_REQUIRED", "采购收货仅接受原材料");
        }
        requirePositive(quantity);
        receiveProduct(materialId, quantity, "PURCHASE_RECEIPT", "PURCHASE_ORDER", purchaseOrderId,
                "采购到货", actor);
    }

    @Transactional
    public void receiveFinishedGoods(long inspectionId, long productId, BigDecimal quantity, String actor) {
        Product product = requireEnabledProduct(productId);
        if (!"FINISHED".equals(product.getProductType())) {
            throw BusinessRuleException.badRequest("FINISHED_PRODUCT_REQUIRED", "质检入库只能接收成品");
        }
        requirePositive(quantity);
        if (transactionMapper.countFinishedGoodsReceipt(inspectionId) > 0) {
            throw BusinessRuleException.conflict("INSPECTION_ALREADY_RECEIVED", "该质检记录已经入库，不能重复入账");
        }
        balanceMapper.ensureBalance(productId);
        try {
            if (transactionMapper.insertFinishedGoodsReceipt(inspectionId, productId, quantity, actor) != 1) {
                throw new IllegalStateException("成品入库单写入失败");
            }
        } catch (DuplicateKeyException duplicate) {
            throw BusinessRuleException.conflict("INSPECTION_ALREADY_RECEIVED", "该质检记录已经入库，不能重复入账");
        }
        if (balanceMapper.increaseOnHand(productId, quantity) != 1) {
            throw new IllegalStateException("成品库存余额更新失败");
        }
        recordMovement(productId, "FINISHED_GOODS_RECEIPT", quantity, "QUALITY_INSPECTION", inspectionId,
                "质检合格成品入库", actor);
    }

    @Transactional(readOnly = true)
    public boolean hasFinishedGoodsReceipt(long inspectionId) {
        return transactionMapper.countFinishedGoodsReceipt(inspectionId) > 0;
    }

    private void receiveProduct(long productId, BigDecimal quantity, String type, String referenceType,
                                Long referenceId, String reason, String actor) {
        balanceMapper.ensureBalance(productId);
        if (balanceMapper.increaseOnHand(productId, quantity) != 1) {
            throw new IllegalStateException("库存余额更新失败");
        }
        recordMovement(productId, type, quantity, referenceType, referenceId, reason, actor);
    }

    private void recordMovement(long productId, String type, BigDecimal quantity, String referenceType,
                                Long referenceId, String reason, String actor) {
        InventoryTransaction movement = new InventoryTransaction();
        movement.setProductId(productId);
        movement.setTransactionType(type);
        movement.setQuantity(quantity);
        movement.setReferenceType(referenceType);
        movement.setReferenceId(referenceId);
        movement.setReason(reason);
        movement.setActor(actor);
        if (transactionMapper.insert(movement) != 1) {
            throw new IllegalStateException("库存流水写入失败");
        }
    }

    private List<OrderMaterialRequirement> requireSnapshot(long orderId) {
        List<OrderMaterialRequirement> requirements = requirementMapper.selectByOrderId(orderId);
        if (requirements == null || requirements.isEmpty()) {
            throw BusinessRuleException.conflict("ORDER_BOM_SNAPSHOT_MISSING", "订单没有物料需求快照，请重新创建生产订单");
        }
        return requirements;
    }

    private ProductionOrder requireOrder(long orderId) {
        ProductionOrder order = orderMapper.selectById(orderId);
        if (order == null) throw BusinessRuleException.notFound("PRODUCTION_ORDER_NOT_FOUND", "生产订单不存在");
        return order;
    }

    private static void requireApproved(ProductionOrder order) {
        if (!"APPROVED".equals(order.getStatus())) {
            throw BusinessRuleException.conflict("ORDER_NOT_APPROVED", "只有已批准订单可以进行备料和领料");
        }
    }

    private Product requireEnabledProduct(long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null || !product.isEnabled()) {
            throw BusinessRuleException.notFound("PRODUCT_NOT_FOUND", "找不到已启用的物料或产品");
        }
        return product;
    }

    private static void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw BusinessRuleException.badRequest("INVALID_STOCK_QUANTITY", "库存数量必须为正数");
        }
    }

    private static InventoryBalance emptyBalance(long productId) {
        InventoryBalance balance = new InventoryBalance();
        balance.setProductId(productId);
        balance.setOnHandQuantity(ZERO);
        balance.setReservedQuantity(ZERO);
        return balance;
    }

    private static BigDecimal value(BigDecimal quantity) {
        return quantity == null ? ZERO : quantity;
    }

    private static BigDecimal maxZero(BigDecimal quantity) {
        return quantity.signum() < 0 ? ZERO : quantity;
    }
}
