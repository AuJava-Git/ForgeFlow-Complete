CREATE TABLE quality_inspection (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    report_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    passed_quantity INT NOT NULL,
    rejected_quantity INT NOT NULL,
    result VARCHAR(16) NOT NULL,
    inspector VARCHAR(64) NOT NULL,
    remark VARCHAR(500) NULL,
    inspected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_quality_inspection_report UNIQUE (report_id),
    CONSTRAINT chk_quality_inspection_quantity CHECK (passed_quantity >= 0 AND rejected_quantity >= 0),
    CONSTRAINT chk_quality_inspection_result CHECK (result IN ('PASS', 'PARTIAL', 'REJECTED')),
    CONSTRAINT fk_quality_inspection_report FOREIGN KEY (report_id) REFERENCES mes_production_report(id),
    CONSTRAINT fk_quality_inspection_order FOREIGN KEY (order_id) REFERENCES erp_production_order(id),
    INDEX idx_quality_inspection_order (order_id, id)
);

CREATE TABLE inv_finished_goods_receipt (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    inspection_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity DECIMAL(14, 4) NOT NULL,
    received_by VARCHAR(64) NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_finished_receipt_inspection UNIQUE (inspection_id),
    CONSTRAINT chk_finished_receipt_quantity CHECK (quantity > 0),
    CONSTRAINT fk_finished_receipt_inspection FOREIGN KEY (inspection_id) REFERENCES quality_inspection(id),
    CONSTRAINT fk_finished_receipt_product FOREIGN KEY (product_id) REFERENCES erp_product(id),
    INDEX idx_finished_receipt_product (product_id, id)
);
