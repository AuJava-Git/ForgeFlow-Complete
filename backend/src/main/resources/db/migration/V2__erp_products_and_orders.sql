CREATE TABLE erp_product (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    product_type VARCHAR(16) NOT NULL,
    unit VARCHAR(16) NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_erp_product_code UNIQUE (code),
    CONSTRAINT chk_erp_product_type CHECK (product_type IN ('FINISHED', 'MATERIAL'))
);

CREATE TABLE erp_bom_line (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    quantity DECIMAL(14, 4) NOT NULL,
    CONSTRAINT uk_erp_bom_product_material UNIQUE (product_id, material_id),
    CONSTRAINT chk_erp_bom_quantity CHECK (quantity > 0),
    CONSTRAINT fk_erp_bom_product FOREIGN KEY (product_id) REFERENCES erp_product(id),
    CONSTRAINT fk_erp_bom_material FOREIGN KEY (material_id) REFERENCES erp_product(id)
);

CREATE TABLE erp_production_order (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    product_id BIGINT NOT NULL,
    planned_quantity INT NOT NULL,
    good_quantity INT NOT NULL DEFAULT 0,
    defect_quantity INT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_erp_production_order_no UNIQUE (order_no),
    CONSTRAINT chk_erp_order_quantities CHECK (planned_quantity > 0 AND good_quantity >= 0 AND defect_quantity >= 0),
    CONSTRAINT chk_erp_order_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'IN_PROGRESS', 'COMPLETED')),
    CONSTRAINT fk_erp_order_product FOREIGN KEY (product_id) REFERENCES erp_product(id),
    INDEX idx_erp_order_status (status)
);
