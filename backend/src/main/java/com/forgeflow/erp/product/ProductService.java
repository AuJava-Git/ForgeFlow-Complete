package com.forgeflow.erp.product;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.forgeflow.platform.error.BusinessRuleException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ProductService {
    private final ProductMapper productMapper;
    private final BomLineMapper bomLineMapper;

    public ProductService(ProductMapper productMapper, BomLineMapper bomLineMapper) {
        this.productMapper = productMapper;
        this.bomLineMapper = bomLineMapper;
    }

    @Transactional
    public ProductView create(ProductRequest request) {
        if (request.code() == null || request.code().isBlank()) {
            throw BusinessRuleException.badRequest("INVALID_PRODUCT_CODE", "产品编码不能为空");
        }
        if (productMapper.selectCount(Wrappers.<Product>lambdaQuery().eq(Product::getCode, request.code())) > 0) {
            throw BusinessRuleException.conflict("DUPLICATE_PRODUCT_CODE", "产品编码已存在: " + request.code());
        }
        if (!"FINISHED".equals(request.type()) && !"MATERIAL".equals(request.type())) {
            throw BusinessRuleException.badRequest("INVALID_PRODUCT_TYPE", "产品类型必须为 FINISHED 或 MATERIAL");
        }
        Product product = new Product();
        product.setCode(request.code().trim());
        product.setName(request.name().trim());
        product.setProductType(request.type());
        product.setUnit(request.unit().trim());
        product.setEnabled(request.enabled());
        productMapper.insert(product);
        return view(product);
    }

    public List<ProductView> list() {
        return productMapper.selectList(Wrappers.<Product>query().orderByAsc("id")).stream()
                .map(ProductService::view).toList();
    }

    public List<BomLineView> bom(long productId) {
        Product finished = productMapper.selectById(productId);
        if (finished == null || !"FINISHED".equals(finished.getProductType())) {
            throw BusinessRuleException.notFound("FINISHED_PRODUCT_NOT_FOUND", "找不到成品");
        }
        return bomLineMapper.selectList(Wrappers.<BomLine>lambdaQuery()
                        .eq(BomLine::getProductId, productId)
                        .orderByAsc(BomLine::getId))
                .stream()
                .map(line -> {
                    Product material = productMapper.selectById(line.getMaterialId());
                    return new BomLineView(line.getMaterialId(), material == null ? "" : material.getCode(),
                            material == null ? "" : material.getName(), material == null ? "" : material.getUnit(),
                            line.getQuantity());
                })
                .toList();
    }

    @Transactional
    public void replaceBom(long productId, BomRequest request) {
        Product finished = productMapper.selectById(productId);
        if (finished == null || !finished.isEnabled() || !"FINISHED".equals(finished.getProductType())) {
            throw BusinessRuleException.notFound("FINISHED_PRODUCT_NOT_FOUND", "找不到已启用的成品");
        }
        Set<Long> materialIds = new HashSet<>();
        List<BomLine> newLines = new ArrayList<>();
        for (BomLineRequest item : request.items()) {
            if (item.quantity() == null || item.quantity().signum() <= 0) {
                throw BusinessRuleException.badRequest("INVALID_BOM_QUANTITY", "BOM用量必须为正数");
            }
            if (!materialIds.add(item.materialId())) {
                throw BusinessRuleException.conflict("DUPLICATE_BOM_MATERIAL", "同一BOM不能重复添加原料");
            }
            Product material = productMapper.selectById(item.materialId());
            if (material == null || !material.isEnabled() || !"MATERIAL".equals(material.getProductType())) {
                throw BusinessRuleException.badRequest("INVALID_BOM_MATERIAL", "BOM引用的原料不存在、已停用或类型错误");
            }
            BomLine line = new BomLine();
            line.setProductId(productId);
            line.setMaterialId(material.getId());
            line.setQuantity(item.quantity());
            newLines.add(line);
        }
        bomLineMapper.delete(Wrappers.<BomLine>lambdaQuery().eq(BomLine::getProductId, productId));
        newLines.forEach(bomLineMapper::insert);
    }

    private static ProductView view(Product product) {
        return new ProductView(product.getId(), product.getCode(), product.getName(),
                product.getProductType(), product.getUnit(), product.isEnabled());
    }
}
