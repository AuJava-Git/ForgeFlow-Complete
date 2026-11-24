-- ForgeFlow local workbench seed. This migration adds stable business keys and never resets an existing balance.

INSERT INTO sys_user (username, password_hash, display_name, role_code)
VALUES
    ('demo-warehouse', '${demo_password_hash}', '演示仓库员', 'WAREHOUSE'),
    ('demo-purchaser', '${demo_password_hash}', '演示采购员', 'PURCHASER'),
    ('demo-quality', '${demo_password_hash}', '演示质检员', 'QUALITY_INSPECTOR')
ON DUPLICATE KEY UPDATE username = VALUES(username);

INSERT INTO erp_product (code, name, product_type, unit, enabled)
VALUES
    ('FG-DEMO-002', '工业传感器模组', 'FINISHED', 'pcs', 1),
    ('FG-DEMO-003', '伺服执行器', 'FINISHED', 'pcs', 1),
    ('FG-DEMO-004', '边缘采集网关', 'FINISHED', 'pcs', 1),
    ('RM-DEMO-003', '工业传感器', 'MATERIAL', 'pcs', 1),
    ('RM-DEMO-004', '铝合金外壳', 'MATERIAL', 'pcs', 1),
    ('RM-DEMO-005', '伺服电机', 'MATERIAL', 'pcs', 1),
    ('RM-DEMO-006', '线束组件', 'MATERIAL', 'pcs', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), product_type = VALUES(product_type), unit = VALUES(unit), enabled = VALUES(enabled);

INSERT INTO erp_bom_line (product_id, material_id, quantity)
SELECT finished.id, material.id, bom.quantity
FROM (
    SELECT 'FG-DEMO-001' AS finished_code, 'RM-DEMO-006' AS material_code, 1.0000 AS quantity
    UNION ALL SELECT 'FG-DEMO-002', 'RM-DEMO-003', 2.0000
    UNION ALL SELECT 'FG-DEMO-002', 'RM-DEMO-004', 1.0000
    UNION ALL SELECT 'FG-DEMO-002', 'RM-DEMO-006', 2.0000
    UNION ALL SELECT 'FG-DEMO-003', 'RM-DEMO-002', 6.0000
    UNION ALL SELECT 'FG-DEMO-003', 'RM-DEMO-004', 1.0000
    UNION ALL SELECT 'FG-DEMO-003', 'RM-DEMO-005', 1.0000
    UNION ALL SELECT 'FG-DEMO-004', 'RM-DEMO-001', 1.0000
    UNION ALL SELECT 'FG-DEMO-004', 'RM-DEMO-004', 2.0000
    UNION ALL SELECT 'FG-DEMO-004', 'RM-DEMO-006', 3.0000
) bom
JOIN erp_product finished ON finished.code = bom.finished_code
JOIN erp_product material ON material.code = bom.material_code
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity);

-- Additional material stock starts with a bounded sample balance; reruns preserve any later user changes.
INSERT INTO inv_stock_balance (product_id, on_hand_quantity, reserved_quantity)
SELECT p.id,
       CASE p.code
           WHEN 'RM-DEMO-003' THEN 100.0000
           WHEN 'RM-DEMO-004' THEN 100.0000
           WHEN 'RM-DEMO-005' THEN 50.0000
           WHEN 'RM-DEMO-006' THEN 100.0000
       END,
       0.0000
FROM erp_product p
WHERE p.code IN ('RM-DEMO-003', 'RM-DEMO-004', 'RM-DEMO-005', 'RM-DEMO-006')
ON DUPLICATE KEY UPDATE product_id = VALUES(product_id);

-- Add one-time stock transactions for the completed gateway lot and the other
-- issued/reserved examples, leaving exactly 30 boards and 80 screws for the shortage order.
UPDATE inv_stock_balance balance
JOIN erp_product material ON material.id = balance.product_id AND material.code = 'RM-DEMO-001'
SET balance.on_hand_quantity = balance.on_hand_quantity + 10.0000
WHERE NOT EXISTS (
    SELECT 1 FROM inv_stock_transaction movement
    WHERE movement.product_id = material.id AND movement.reference_type = 'DEMO_SEED'
      AND movement.reference_id = 2001
);

INSERT INTO inv_stock_transaction (product_id, transaction_type, quantity, reference_type, reference_id, reason, actor)
SELECT material.id, 'OPENING_RECEIPT', 10.0000, 'DEMO_SEED', 2001, 'ForgeFlow 多岗位样例一次性控制板库存补充', 'demo-seed'
FROM erp_product material
WHERE material.code = 'RM-DEMO-001'
  AND NOT EXISTS (
      SELECT 1 FROM inv_stock_transaction movement
      WHERE movement.product_id = material.id AND movement.reference_type = 'DEMO_SEED'
        AND movement.reference_id = 2001
  );

UPDATE inv_stock_balance balance
JOIN erp_product material ON material.id = balance.product_id AND material.code = 'RM-DEMO-002'
SET balance.on_hand_quantity = balance.on_hand_quantity + 42.0000
WHERE NOT EXISTS (
    SELECT 1 FROM inv_stock_transaction movement
    WHERE movement.product_id = material.id AND movement.reference_type = 'DEMO_SEED'
      AND movement.reference_id = 2002
);

INSERT INTO inv_stock_transaction (product_id, transaction_type, quantity, reference_type, reference_id, reason, actor)
SELECT material.id, 'OPENING_RECEIPT', 42.0000, 'DEMO_SEED', 2002, 'ForgeFlow 多岗位样例一次性螺丝库存补充', 'demo-seed'
FROM erp_product material
WHERE material.code = 'RM-DEMO-002'
  AND NOT EXISTS (
      SELECT 1 FROM inv_stock_transaction movement
      WHERE movement.product_id = material.id AND movement.reference_type = 'DEMO_SEED'
        AND movement.reference_id = 2002
  );

INSERT INTO inv_stock_transaction (product_id, transaction_type, quantity, reference_type, reference_id, reason, actor)
SELECT p.id, 'OPENING_RECEIPT', b.on_hand_quantity, 'DEMO_SEED', p.id, 'ForgeFlow 本地演示期初库存', 'demo-seed'
FROM erp_product p
JOIN inv_stock_balance b ON b.product_id = p.id
WHERE p.code IN ('RM-DEMO-003', 'RM-DEMO-004', 'RM-DEMO-005', 'RM-DEMO-006')
  AND NOT EXISTS (
      SELECT 1 FROM inv_stock_transaction t
      WHERE t.product_id = p.id AND t.transaction_type = 'OPENING_RECEIPT'
        AND t.reference_type = 'DEMO_SEED' AND t.reference_id = p.id
  );

-- One draft, one pending approval, one shortage order, one rejection, one MES order,
-- one completed lot and one fully reserved order keep every role queue populated.
INSERT INTO erp_production_order
    (order_no, product_id, planned_quantity, good_quantity, defect_quantity, status, material_status, created_by)
SELECT seed.order_no, product.id, seed.planned_quantity, seed.good_quantity, seed.defect_quantity,
       seed.status, seed.material_status, seed.created_by
FROM (
    SELECT 'DEMO-DRAFT-001' AS order_no, 'FG-DEMO-003' AS product_code, 6 AS planned_quantity,
           0 AS good_quantity, 0 AS defect_quantity, 'DRAFT' AS status, 'NOT_READY' AS material_status,
           'demo-planner' AS created_by
    UNION ALL SELECT 'DEMO-PENDING-001', 'FG-DEMO-004', 6, 0, 0, 'PENDING_APPROVAL', 'NOT_READY', 'demo-planner'
    UNION ALL SELECT 'DEMO-SHORT-001', 'FG-DEMO-001', 20, 0, 0, 'APPROVED', 'NOT_READY', 'demo-planner'
    UNION ALL SELECT 'DEMO-REJECT-001', 'FG-DEMO-001', 5, 0, 0, 'REJECTED', 'NOT_READY', 'demo-planner'
    UNION ALL SELECT 'DEMO-PROD-001', 'FG-DEMO-003', 4, 2, 0, 'IN_PROGRESS', 'ISSUED', 'demo-planner'
    UNION ALL SELECT 'DEMO-DONE-001', 'FG-DEMO-004', 10, 9, 1, 'COMPLETED', 'ISSUED', 'demo-planner'
    UNION ALL SELECT 'DEMO-READY-001', 'FG-DEMO-003', 1, 0, 0, 'APPROVED', 'RESERVED', 'demo-planner'
    UNION ALL SELECT 'DEMO-START-001', 'FG-DEMO-003', 2, 0, 0, 'APPROVED', 'ISSUED', 'demo-planner'
) seed
JOIN erp_product product ON product.code = seed.product_code
WHERE NOT EXISTS (
    SELECT 1 FROM erp_production_order existing WHERE existing.order_no = seed.order_no
);

INSERT INTO erp_order_material_requirement
    (order_id, material_id, required_quantity, reserved_quantity, issued_quantity)
SELECT o.id, b.material_id, b.quantity * o.planned_quantity,
       CASE WHEN o.order_no IN ('DEMO-PROD-001', 'DEMO-DONE-001', 'DEMO-READY-001', 'DEMO-START-001')
            THEN b.quantity * o.planned_quantity ELSE 0.0000 END,
       CASE WHEN o.order_no IN ('DEMO-PROD-001', 'DEMO-DONE-001', 'DEMO-START-001')
            THEN b.quantity * o.planned_quantity ELSE 0.0000 END
FROM erp_production_order o
JOIN erp_bom_line b ON b.product_id = o.product_id
WHERE o.order_no LIKE 'DEMO-%'
  AND NOT EXISTS (
      SELECT 1 FROM erp_order_material_requirement existing
      WHERE existing.order_id = o.id AND existing.material_id = b.material_id
  );

INSERT INTO oa_approval_action (order_id, action_code, actor, comment)
SELECT o.id, 'SUBMIT', 'demo-planner', '演示计划已提交审批'
FROM erp_production_order o
WHERE o.order_no IN ('DEMO-PENDING-001', 'DEMO-SHORT-001', 'DEMO-REJECT-001', 'DEMO-PROD-001', 'DEMO-DONE-001', 'DEMO-READY-001', 'DEMO-START-001')
  AND NOT EXISTS (
      SELECT 1 FROM oa_approval_action a WHERE a.order_id = o.id AND a.action_code = 'SUBMIT'
  );

INSERT INTO oa_approval_action (order_id, action_code, actor, comment)
SELECT o.id, 'APPROVE', 'demo-approver', '演示审批通过，进入后续制造流程'
FROM erp_production_order o
WHERE o.order_no IN ('DEMO-SHORT-001', 'DEMO-PROD-001', 'DEMO-DONE-001', 'DEMO-READY-001', 'DEMO-START-001')
  AND NOT EXISTS (
      SELECT 1 FROM oa_approval_action a WHERE a.order_id = o.id AND a.action_code = 'APPROVE'
  );

INSERT INTO oa_approval_action (order_id, action_code, actor, comment)
SELECT o.id, 'REJECT', 'demo-approver', '演示驳回：交付窗口需要重新评估'
FROM erp_production_order o
WHERE o.order_no = 'DEMO-REJECT-001'
  AND NOT EXISTS (
      SELECT 1 FROM oa_approval_action a WHERE a.order_id = o.id AND a.action_code = 'REJECT'
  );

-- The in-progress and completed orders consumed their materials; the reserved order
-- holds available stock. Movements are the idempotency keys for these balance changes.
INSERT INTO inv_stock_transaction (product_id, transaction_type, quantity, reference_type, reference_id, reason, actor)
SELECT requirement.material_id, 'RESERVE', requirement.issued_quantity, 'PRODUCTION_ORDER', production_order.id,
       '演示生产订单备料预留', 'demo-warehouse'
FROM erp_order_material_requirement requirement
JOIN erp_production_order production_order ON production_order.id = requirement.order_id
WHERE production_order.order_no IN ('DEMO-PROD-001', 'DEMO-DONE-001', 'DEMO-START-001')
  AND requirement.issued_quantity > 0
  AND NOT EXISTS (
      SELECT 1 FROM inv_stock_transaction movement
      WHERE movement.product_id = requirement.material_id AND movement.transaction_type = 'RESERVE'
        AND movement.reference_type = 'PRODUCTION_ORDER' AND movement.reference_id = production_order.id
  );

UPDATE inv_stock_balance balance
JOIN (
    SELECT requirement.material_id, SUM(requirement.issued_quantity) AS issue_quantity
    FROM erp_order_material_requirement requirement
    JOIN erp_production_order production_order ON production_order.id = requirement.order_id
    WHERE production_order.order_no IN ('DEMO-PROD-001', 'DEMO-DONE-001', 'DEMO-START-001')
      AND requirement.issued_quantity > 0
      AND NOT EXISTS (
          SELECT 1 FROM inv_stock_transaction movement
          WHERE movement.product_id = requirement.material_id AND movement.transaction_type = 'ISSUE'
            AND movement.reference_type = 'PRODUCTION_ORDER' AND movement.reference_id = production_order.id
      )
    GROUP BY requirement.material_id
) issued ON issued.material_id = balance.product_id
SET balance.on_hand_quantity = balance.on_hand_quantity - issued.issue_quantity;

INSERT INTO inv_stock_transaction (product_id, transaction_type, quantity, reference_type, reference_id, reason, actor)
SELECT requirement.material_id, 'ISSUE', requirement.issued_quantity, 'PRODUCTION_ORDER', production_order.id,
       '演示生产订单领料', 'demo-warehouse'
FROM erp_order_material_requirement requirement
JOIN erp_production_order production_order ON production_order.id = requirement.order_id
WHERE production_order.order_no IN ('DEMO-PROD-001', 'DEMO-DONE-001', 'DEMO-START-001')
  AND requirement.issued_quantity > 0
  AND NOT EXISTS (
      SELECT 1 FROM inv_stock_transaction movement
      WHERE movement.product_id = requirement.material_id AND movement.transaction_type = 'ISSUE'
        AND movement.reference_type = 'PRODUCTION_ORDER' AND movement.reference_id = production_order.id
  );

UPDATE inv_stock_balance balance
JOIN erp_order_material_requirement requirement ON requirement.material_id = balance.product_id
JOIN erp_production_order production_order ON production_order.id = requirement.order_id
SET balance.reserved_quantity = balance.reserved_quantity + requirement.reserved_quantity
WHERE production_order.order_no = 'DEMO-READY-001'
  AND requirement.reserved_quantity > 0
  AND NOT EXISTS (
      SELECT 1 FROM inv_stock_transaction movement
      WHERE movement.product_id = balance.product_id AND movement.transaction_type = 'RESERVE'
        AND movement.reference_type = 'PRODUCTION_ORDER' AND movement.reference_id = production_order.id
  );

INSERT INTO inv_stock_transaction (product_id, transaction_type, quantity, reference_type, reference_id, reason, actor)
SELECT requirement.material_id, 'RESERVE', requirement.reserved_quantity, 'PRODUCTION_ORDER', production_order.id,
       '演示生产订单备料预留', 'demo-warehouse'
FROM erp_order_material_requirement requirement
JOIN erp_production_order production_order ON production_order.id = requirement.order_id
WHERE production_order.order_no = 'DEMO-READY-001'
  AND requirement.reserved_quantity > 0
  AND NOT EXISTS (
      SELECT 1 FROM inv_stock_transaction movement
      WHERE movement.product_id = requirement.material_id AND movement.transaction_type = 'RESERVE'
        AND movement.reference_type = 'PRODUCTION_ORDER' AND movement.reference_id = production_order.id
  );

-- Finished goods inventory rows are inserted without overwriting balances.
INSERT INTO inv_stock_balance (product_id, on_hand_quantity, reserved_quantity)
SELECT id, 0.0000, 0.0000 FROM erp_product WHERE code LIKE 'FG-DEMO-%'
ON DUPLICATE KEY UPDATE product_id = VALUES(product_id);

INSERT INTO mes_production_report (order_id, good_quantity, defect_quantity, operator, remark)
SELECT o.id, 2, 0, 'demo-operator', '演示待检批次'
FROM erp_production_order o
WHERE o.order_no = 'DEMO-PROD-001'
  AND NOT EXISTS (
      SELECT 1 FROM mes_production_report r
      WHERE r.order_id = o.id AND r.operator = 'demo-operator' AND r.remark = '演示待检批次'
  );

INSERT INTO mes_production_report (order_id, good_quantity, defect_quantity, operator, remark)
SELECT o.id, 5, 0, 'demo-operator', '完工订单首批检验'
FROM erp_production_order o
WHERE o.order_no = 'DEMO-DONE-001'
  AND NOT EXISTS (
      SELECT 1 FROM mes_production_report r
      WHERE r.order_id = o.id AND r.operator = 'demo-operator' AND r.remark = '完工订单首批检验'
  );

INSERT INTO mes_production_report (order_id, good_quantity, defect_quantity, operator, remark)
SELECT o.id, 4, 1, 'demo-operator', '完工订单尾批检验'
FROM erp_production_order o
WHERE o.order_no = 'DEMO-DONE-001'
  AND NOT EXISTS (
      SELECT 1 FROM mes_production_report r
      WHERE r.order_id = o.id AND r.operator = 'demo-operator' AND r.remark = '完工订单尾批检验'
  );

INSERT INTO quality_inspection (report_id, order_id, passed_quantity, rejected_quantity, result, inspector, remark)
SELECT r.id, r.order_id, 5, 0, 'PASS', 'demo-quality', '首批抽检通过'
FROM mes_production_report r
JOIN erp_production_order o ON o.id = r.order_id
WHERE o.order_no = 'DEMO-DONE-001' AND r.remark = '完工订单首批检验'
  AND NOT EXISTS (SELECT 1 FROM quality_inspection existing WHERE existing.report_id = r.id);

INSERT INTO quality_inspection (report_id, order_id, passed_quantity, rejected_quantity, result, inspector, remark)
SELECT r.id, r.order_id, 3, 1, 'PARTIAL', 'demo-quality', '尾批抽检发现一件不合格'
FROM mes_production_report r
JOIN erp_production_order o ON o.id = r.order_id
WHERE o.order_no = 'DEMO-DONE-001' AND r.remark = '完工订单尾批检验'
  AND NOT EXISTS (SELECT 1 FROM quality_inspection existing WHERE existing.report_id = r.id);

INSERT INTO inv_finished_goods_receipt (inspection_id, product_id, quantity, received_by)
SELECT inspection.id, product.id, inspection.passed_quantity, 'demo-quality'
FROM quality_inspection inspection
JOIN erp_production_order production_order ON production_order.id = inspection.order_id
JOIN erp_product product ON product.id = production_order.product_id
WHERE production_order.order_no = 'DEMO-DONE-001' AND inspection.passed_quantity > 0
  AND NOT EXISTS (
      SELECT 1 FROM inv_finished_goods_receipt existing WHERE existing.inspection_id = inspection.id
  );

UPDATE inv_stock_balance balance
JOIN (
    SELECT receipt.product_id, SUM(receipt.quantity) AS received_quantity
    FROM inv_finished_goods_receipt receipt
    JOIN quality_inspection inspection ON inspection.id = receipt.inspection_id
    JOIN erp_production_order production_order ON production_order.id = inspection.order_id
    WHERE production_order.order_no = 'DEMO-DONE-001'
      AND NOT EXISTS (
          SELECT 1 FROM inv_stock_transaction movement
          WHERE movement.product_id = receipt.product_id AND movement.transaction_type = 'FINISHED_GOODS_RECEIPT'
            AND movement.reference_type = 'QUALITY_INSPECTION' AND movement.reference_id = inspection.id
      )
    GROUP BY receipt.product_id
) receipts ON receipts.product_id = balance.product_id
SET balance.on_hand_quantity = balance.on_hand_quantity + receipts.received_quantity;

INSERT INTO inv_stock_transaction (product_id, transaction_type, quantity, reference_type, reference_id, reason, actor)
SELECT receipt.product_id, 'FINISHED_GOODS_RECEIPT', receipt.quantity, 'QUALITY_INSPECTION', inspection.id,
       '演示质检合格成品入库', 'demo-quality'
FROM inv_finished_goods_receipt receipt
JOIN quality_inspection inspection ON inspection.id = receipt.inspection_id
JOIN erp_production_order production_order ON production_order.id = inspection.order_id
WHERE production_order.order_no = 'DEMO-DONE-001'
  AND NOT EXISTS (
      SELECT 1 FROM inv_stock_transaction movement
      WHERE movement.product_id = receipt.product_id AND movement.transaction_type = 'FINISHED_GOODS_RECEIPT'
        AND movement.reference_type = 'QUALITY_INSPECTION' AND movement.reference_id = inspection.id
  );

INSERT INTO purchasing_order (order_no, production_order_id, status, created_by)
SELECT 'DEMO-PO-001', production_order.id, 'OPEN', 'demo-purchaser'
FROM erp_production_order production_order
WHERE production_order.order_no = 'DEMO-SHORT-001'
  AND NOT EXISTS (SELECT 1 FROM purchasing_order existing WHERE existing.order_no = 'DEMO-PO-001');

INSERT INTO purchasing_order_line (purchase_order_id, material_id, ordered_quantity, received_quantity)
SELECT purchase_order.id, requirement.material_id,
       GREATEST(requirement.required_quantity - (balance.on_hand_quantity - balance.reserved_quantity), 0.0000),
       0.0000
FROM purchasing_order purchase_order
JOIN erp_production_order production_order ON production_order.id = purchase_order.production_order_id
JOIN erp_order_material_requirement requirement ON requirement.order_id = production_order.id
JOIN inv_stock_balance balance ON balance.product_id = requirement.material_id
WHERE purchase_order.order_no = 'DEMO-PO-001'
  AND GREATEST(requirement.required_quantity - (balance.on_hand_quantity - balance.reserved_quantity), 0.0000) > 0
  AND NOT EXISTS (
      SELECT 1 FROM purchasing_order_line existing
      WHERE existing.purchase_order_id = purchase_order.id AND existing.material_id = requirement.material_id
  );
