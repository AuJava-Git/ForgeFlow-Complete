package com.forgeflow.erp.product;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/erp/products")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<ProductView> list() {
        return productService.list();
    }

    @GetMapping("/{id}/bom")
    public List<BomLineView> bom(@PathVariable long id) {
        return productService.bom(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductView create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}/bom")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replaceBom(@PathVariable long id, @Valid @RequestBody BomRequest request) {
        productService.replaceBom(id, request);
    }
}
