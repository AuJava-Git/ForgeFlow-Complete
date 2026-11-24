CREATE TABLE mes_production_report (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    good_quantity INT NOT NULL,
    defect_quantity INT NOT NULL,
    operator VARCHAR(64) NOT NULL,
    remark VARCHAR(500) NULL,
    reported_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mes_report_quantities CHECK (good_quantity >= 0 AND defect_quantity >= 0 AND good_quantity + defect_quantity > 0),
    CONSTRAINT fk_mes_report_order FOREIGN KEY (order_id) REFERENCES erp_production_order(id),
    INDEX idx_mes_report_order (order_id, id)
);
