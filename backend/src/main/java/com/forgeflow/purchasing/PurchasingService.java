package com.forgeflow.purchasing;

import com.forgeflow.erp.order.ProductionOrder;
import com.forgeflow.erp.order.ProductionOrderMapper;
import com.forgeflow.erp.product.Product;
import com.forgeflow.erp.product.ProductMapper;
import com.forgeflow.inventory.InventoryDtos.MaterialRequirementView;
import com.forgeflow.inventory.InventoryService;
import com.forgeflow.platform.error.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.forgeflow.purchasing.PurchaseOrderDtos.PurchaseOrderLineView;
import static com.forgeflow.purchasing.PurchaseOrderDtos.PurchaseOrderView;
import static com.forgeflow.purchasing.PurchaseOrderDtos.PurchaseReceiptLineRequest;
import static com.forgeflow.purchasing.PurchaseOrderDtos.PurchaseReceiptRequest;

@Service
public class PurchasingService {
    private final InventoryService inventoryService;
    private final ProductionOrderMapper orderMapper;
    private final PurchaseOrderMapper purchaseOrderMapper;
    private final PurchaseOrderLineMapper lineMapper;
    private final ProductMapper productMapper;

    public PurchasingService(InventoryService inventoryService, ProductionOrderMapper orderMapper,
                             PurchaseOrderMapper purchaseOrderMapper, PurchaseOrderLineMapper lineMapper,
                             ProductMapper productMapper) {
        this.inventoryService = inventoryService;
        this.orderMapper = orderMapper;
        this.purchaseOrderMapper = purchaseOrderMapper;
        this.lineMapper = lineMapper;
        this.productMapper = productMapper;
    }

    @Transactional
    public PurchaseOrderView createForShortages(long productionOrderId, String actor) {
        ProductionOrder productionOrder = orderMapper.selectById(productionOrderId);
        if (productionOrder == null) throw BusinessRuleException.notFound("PRODUCTION_ORDER_NOT_FOUND", "生产订单不存在");
        if (!"APPROVED".equals(productionOrder.getStatus())) {
            throw BusinessRuleException.conflict("ORDER_NOT_APPROVED", "只有已批准订单可以生成补料采购单");
        }
        if (purchaseOrderMapper.selectByProductionOrderId(productionOrderId) != null) {
            throw BusinessRuleException.conflict("SHORTAGE_ORDER_ALREADY_EXISTS", "该生产订单已经生成过补料采购单");
        }
        List<MaterialRequirementView> shortages = inventoryService.requirements(productionOrderId).stream()
                .filter(row -> row.shortageQuantity().signum() > 0).toList();
        if (shortages.isEmpty()) {
            throw BusinessRuleException.conflict("NO_SHORTAGE", "当前物料库存充足，无需生成采购单");
        }

        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setOrderNo("PO-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        purchaseOrder.setProductionOrderId(productionOrderId);
        purchaseOrder.setStatus("OPEN");
        purchaseOrder.setCreatedBy(actor);
        if (purchaseOrderMapper.insert(purchaseOrder) != 1) throw new IllegalStateException("采购单写入失败");

        List<PurchaseOrderLine> lines = new ArrayList<>();
        for (MaterialRequirementView shortage : shortages) {
            PurchaseOrderLine line = new PurchaseOrderLine();
            line.setPurchaseOrderId(purchaseOrder.getId());
            line.setMaterialId(shortage.materialId());
            line.setOrderedQuantity(shortage.shortageQuantity());
            line.setReceivedQuantity(BigDecimal.ZERO.setScale(4));
            if (lineMapper.insert(line) != 1) throw new IllegalStateException("采购单明细写入失败");
            lines.add(line);
        }
        return view(purchaseOrder, lines);
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrderView> list() {
        return purchaseOrderMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.<PurchaseOrder>query()
                        .orderByDesc("id").last("LIMIT 100"))
                .stream().map(order -> view(order, lineMapper.selectByPurchaseOrderId(order.getId()))).toList();
    }

    @Transactional
    public PurchaseOrderView receive(long purchaseOrderId, PurchaseReceiptRequest request, String actor) {
        PurchaseOrder order = purchaseOrderMapper.selectById(purchaseOrderId);
        if (order == null) throw BusinessRuleException.notFound("PURCHASE_ORDER_NOT_FOUND", "采购单不存在");
        if ("RECEIVED".equals(order.getStatus())) {
            throw BusinessRuleException.conflict("PURCHASE_ORDER_ALREADY_RECEIVED", "采购单已全部到货");
        }
        List<PurchaseOrderLine> lines = lineMapper.selectByPurchaseOrderId(purchaseOrderId);
        List<PurchaseReceiptLineRequest> items = request == null ? null : request.items();
        if (items == null || items.isEmpty()) throw BusinessRuleException.badRequest("RECEIPT_ITEMS_REQUIRED", "至少填写一项到货数量");
        Set<Long> seenMaterials = new HashSet<>();
        List<ReceiptAction> actions = new ArrayList<>();
        for (PurchaseReceiptLineRequest item : items) {
            if (item == null || item.materialId() == null || item.quantity() == null || item.quantity().signum() <= 0) {
                throw BusinessRuleException.badRequest("INVALID_RECEIPT_QUANTITY", "到货数量必须为正数");
            }
            if (!seenMaterials.add(item.materialId())) {
                throw BusinessRuleException.badRequest("DUPLICATE_RECEIPT_MATERIAL", "同一请求中不能重复填写同一物料");
            }
            PurchaseOrderLine line = lines.stream().filter(row -> row.getMaterialId().equals(item.materialId()))
                    .findFirst().orElseThrow(() -> BusinessRuleException.badRequest("MATERIAL_NOT_IN_PURCHASE_ORDER", "收货物料不属于该采购单"));
            BigDecimal outstanding = line.getOrderedQuantity().subtract(line.getReceivedQuantity());
            if (item.quantity().compareTo(outstanding) > 0) {
                throw BusinessRuleException.conflict("RECEIPT_EXCEEDS_OUTSTANDING", "到货数量超过采购单未到数量");
            }
            actions.add(new ReceiptAction(line, item.quantity()));
        }

        for (ReceiptAction action : actions) {
            PurchaseOrderLine line = action.line();
            inventoryService.receivePurchasedMaterial(purchaseOrderId, line.getMaterialId(), action.quantity(), actor);
            if (lineMapper.addReceivedIfWithinOrder(line.getId(), action.quantity()) != 1) {
                throw BusinessRuleException.conflict("RECEIPT_EXCEEDS_OUTSTANDING", "采购单数量已变化，请刷新后重试");
            }
            line.setReceivedQuantity(line.getReceivedQuantity().add(action.quantity()));
        }
        String status = lines.stream().allMatch(line -> line.getReceivedQuantity().compareTo(line.getOrderedQuantity()) >= 0)
                ? "RECEIVED" : "PARTIAL";
        if (purchaseOrderMapper.updateStatus(purchaseOrderId, status) != 1) {
            throw new IllegalStateException("采购单状态更新失败");
        }
        order.setStatus(status);
        return view(order, lines);
    }

    private PurchaseOrderView view(PurchaseOrder order, List<PurchaseOrderLine> lines) {
        List<PurchaseOrderLineView> views = lines.stream().map(line -> {
            Product material = productMapper.selectById(line.getMaterialId());
            BigDecimal received = line.getReceivedQuantity() == null ? BigDecimal.ZERO.setScale(4) : line.getReceivedQuantity();
            return new PurchaseOrderLineView(line.getMaterialId(), material == null ? "" : material.getCode(),
                    material == null ? "" : material.getName(), material == null ? "" : material.getUnit(),
                    line.getOrderedQuantity(), received, line.getOrderedQuantity().subtract(received));
        }).toList();
        return new PurchaseOrderView(order.getId(), order.getOrderNo(), order.getProductionOrderId(), order.getStatus(), views);
    }

    private record ReceiptAction(PurchaseOrderLine line, BigDecimal quantity) {
    }
}
