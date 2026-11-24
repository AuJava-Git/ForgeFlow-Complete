package com.forgeflow.erp.order;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.forgeflow.erp.product.BomLine;
import com.forgeflow.erp.product.BomLineMapper;
import com.forgeflow.erp.product.Product;
import com.forgeflow.erp.product.ProductMapper;
import com.forgeflow.inventory.OrderMaterialRequirement;
import com.forgeflow.inventory.OrderMaterialRequirementMapper;
import com.forgeflow.platform.error.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class ProductionOrderService {
    private final ProductMapper productMapper;
    private final ProductionOrderMapper orderMapper;
    private final BomLineMapper bomLineMapper;
    private final OrderMaterialRequirementMapper requirementMapper;

    public ProductionOrderService(ProductMapper productMapper, ProductionOrderMapper orderMapper,
                                  BomLineMapper bomLineMapper, OrderMaterialRequirementMapper requirementMapper) {
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.bomLineMapper = bomLineMapper;
        this.requirementMapper = requirementMapper;
    }

    @Transactional
    public ProductionOrderView create(ProductionOrderRequest request, String actor) {
        if (request.plannedQuantity() == null || request.plannedQuantity() <= 0) {
            throw BusinessRuleException.badRequest("INVALID_PLANNED_QUANTITY", "计划数量必须为正整数");
        }
        Product product = productMapper.selectById(request.productId());
        if (product == null || !product.isEnabled() || !"FINISHED".equals(product.getProductType())) {
            throw BusinessRuleException.badRequest("INVALID_FINISHED_PRODUCT", "只能为已启用的成品创建生产订单");
        }
        List<BomLine> bomLines = bomLineMapper.selectByProductId(product.getId());
        if (bomLines == null || bomLines.isEmpty()) {
            throw BusinessRuleException.conflict("BOM_REQUIRED_FOR_PRODUCTION", "成品必须先维护 BOM 才能创建生产订单");
        }

        ProductionOrder order = new ProductionOrder();
        order.setOrderNo("FF-" + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        order.setProductId(product.getId());
        order.setPlannedQuantity(request.plannedQuantity());
        order.setGoodQuantity(0);
        order.setDefectQuantity(0);
        order.setStatus("DRAFT");
        order.setMaterialStatus("NOT_READY");
        order.setCreatedBy(actor);
        orderMapper.insert(order);

        for (BomLine bomLine : bomLines) {
            OrderMaterialRequirement requirement = new OrderMaterialRequirement();
            requirement.setOrderId(order.getId());
            requirement.setMaterialId(bomLine.getMaterialId());
            requirement.setRequiredQuantity(BomRequirementCalculator.totalRequired(
                    bomLine.getQuantity(), request.plannedQuantity()));
            requirement.setReservedQuantity(new BigDecimal("0.0000"));
            requirement.setIssuedQuantity(new BigDecimal("0.0000"));
            if (requirementMapper.insert(requirement) != 1) {
                throw new IllegalStateException("订单物料需求快照写入失败");
            }
        }
        return view(order, product);
    }

    public List<ProductionOrderView> list() {
        return orderMapper.selectList(Wrappers.<ProductionOrder>query().orderByDesc("id").last("LIMIT 100")).stream()
                .map(order -> view(order, productMapper.selectById(order.getProductId())))
                .toList();
    }

    public ProductionOrder requireOrder(long id) {
        ProductionOrder order = orderMapper.selectById(id);
        if (order == null) {
            throw BusinessRuleException.notFound("PRODUCTION_ORDER_NOT_FOUND", "生产订单不存在");
        }
        return order;
    }

    private static ProductionOrderView view(ProductionOrder order, Product product) {
        return new ProductionOrderView(order.getId(), order.getOrderNo(), order.getProductId(),
                product == null ? "" : product.getCode(), product == null ? "" : product.getName(),
                order.getPlannedQuantity(), order.getGoodQuantity(), order.getDefectQuantity(), order.getStatus(),
                order.getMaterialStatus());
    }
}
