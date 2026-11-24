package com.forgeflow.erp.product;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.forgeflow.platform.error.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductServiceTest {
    private final ProductMapper productMapper = mock(ProductMapper.class);
    private final BomLineMapper bomLineMapper = mock(BomLineMapper.class);
    private final ProductService productService = new ProductService(productMapper, bomLineMapper);

    @Test
    void rejectsDuplicateCode() {
        when(productMapper.selectCount(any(Wrapper.class))).thenReturn(1L);
        ProductRequest request = new ProductRequest("FG-001", "铝合金壳体", "FINISHED", "件", true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("产品编码已存在: FG-001");
    }

    @Test
    void returnsBomLinesWithMaterialDetails() {
        Product finished = new Product();
        finished.setId(10L);
        finished.setProductType("FINISHED");
        finished.setEnabled(true);
        Product material = new Product();
        material.setId(20L);
        material.setCode("RM-020");
        material.setName("铝合金板");
        material.setUnit("千克");
        material.setProductType("MATERIAL");
        material.setEnabled(true);
        BomLine line = new BomLine();
        line.setProductId(10L);
        line.setMaterialId(20L);
        line.setQuantity(new BigDecimal("2.5000"));
        when(productMapper.selectById(10L)).thenReturn(finished);
        when(productMapper.selectById(20L)).thenReturn(material);
        when(bomLineMapper.selectList(any())).thenReturn(List.of(line));

        assertThat(productService.bom(10L))
                .extracting("materialCode")
                .containsExactly("RM-020");
    }
}
