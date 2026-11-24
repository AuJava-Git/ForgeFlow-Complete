CREATE TABLE purchasing_order (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(40) NOT NULL,
    production_order_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_purchasing_order_no UNIQUE (order_no),
    CONSTRAINT uk_purchasing_order_production_order UNIQUE (production_order_id),
    CONSTRAINT chk_purchasing_order_status CHECK (status IN ('OPEN', 'PARTIAL', 'RECEIVED')),
    CONSTRAINT fk_purchasing_order_production_order FOREIGN KEY (production_order_id) REFERENCES erp_production_order(id),
    INDEX idx_purchasing_order_status (status, id)
);

CREATE TABLE purchasing_order_line (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    purchase_order_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    ordered_quantity DECIMAL(14, 4) NOT NULL,
    received_quantity DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    CONSTRAINT uk_purchasing_order_material UNIQUE (purchase_order_id, material_id),
    CONSTRAINT chk_purchasing_line_quantities CHECK (
        ordered_quantity > 0 AND received_quantity >= 0 AND received_quantity <= ordered_quantity
    ),
    CONSTRAINT fk_purchasing_line_order FOREIGN KEY (purchase_order_id) REFERENCES purchasing_order(id),
    CONSTRAINT fk_purchasing_line_material FOREIGN KEY (material_id) REFERENCES erp_product(id),
    INDEX idx_purchasing_line_order (purchase_order_id, material_id)
);
