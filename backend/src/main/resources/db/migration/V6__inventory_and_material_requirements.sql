ALTER TABLE erp_production_order
    ADD COLUMN material_status VARCHAR(24) NOT NULL DEFAULT 'NOT_READY' AFTER status,
    ADD CONSTRAINT chk_erp_order_material_status
        CHECK (material_status IN ('NOT_READY', 'RESERVED', 'ISSUED'));

CREATE TABLE erp_order_material_requirement (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    material_id BIGINT NOT NULL,
    required_quantity DECIMAL(14, 4) NOT NULL,
    reserved_quantity DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    issued_quantity DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_order_material_requirement UNIQUE (order_id, material_id),
    CONSTRAINT chk_order_material_quantities CHECK (
        required_quantity > 0 AND reserved_quantity >= 0 AND issued_quantity >= 0
        AND reserved_quantity <= required_quantity AND issued_quantity <= reserved_quantity
    ),
    CONSTRAINT fk_order_material_order FOREIGN KEY (order_id) REFERENCES erp_production_order(id),
    CONSTRAINT fk_order_material_product FOREIGN KEY (material_id) REFERENCES erp_product(id),
    INDEX idx_order_material_order (order_id, material_id)
);

CREATE TABLE inv_stock_balance (
    product_id BIGINT NOT NULL PRIMARY KEY,
    on_hand_quantity DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    reserved_quantity DECIMAL(14, 4) NOT NULL DEFAULT 0.0000,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_inventory_balance_nonnegative CHECK (
        on_hand_quantity >= 0 AND reserved_quantity >= 0 AND reserved_quantity <= on_hand_quantity
    ),
    CONSTRAINT fk_inventory_balance_product FOREIGN KEY (product_id) REFERENCES erp_product(id)
);

CREATE TABLE inv_stock_transaction (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    quantity DECIMAL(14, 4) NOT NULL,
    reference_type VARCHAR(32) NOT NULL,
    reference_id BIGINT NULL,
    reason VARCHAR(500) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_inventory_transaction_quantity CHECK (quantity > 0),
    CONSTRAINT chk_inventory_transaction_type CHECK (transaction_type IN (
        'OPENING_RECEIPT', 'PURCHASE_RECEIPT', 'RESERVE', 'ISSUE', 'FINISHED_GOODS_RECEIPT'
    )),
    CONSTRAINT fk_inventory_transaction_product FOREIGN KEY (product_id) REFERENCES erp_product(id),
    INDEX idx_inventory_transaction_product (product_id, id),
    INDEX idx_inventory_transaction_reference (reference_type, reference_id)
);
