CREATE TABLE oa_approval_action (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    action_code VARCHAR(16) NOT NULL,
    actor VARCHAR(64) NOT NULL,
    comment VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_oa_action_code CHECK (action_code IN ('SUBMIT', 'APPROVE', 'REJECT')),
    CONSTRAINT fk_oa_action_order FOREIGN KEY (order_id) REFERENCES erp_production_order(id),
    INDEX idx_oa_action_order (order_id, id)
);
