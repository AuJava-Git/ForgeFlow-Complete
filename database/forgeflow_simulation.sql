-- ForgeFlow 模拟数据（在应用已执行 Flyway V1–V8 后运行）
-- 场景：建立智能控制器 BOM 与偏低控制板库存，创建计划量 20 的订单会短缺 10 块控制板。
-- 本脚本可重复运行，不会重置已经存在的库存余额。

INSERT INTO erp_product (code, name, product_type, unit, enabled)
VALUES
    ('FG-DEMO-001', '智能控制器', 'FINISHED', 'pcs', 1),
    ('RM-DEMO-001', '控制板', 'MATERIAL', 'pcs', 1),
    ('RM-DEMO-002', '紧固螺丝', 'MATERIAL', 'pcs', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name), product_type = VALUES(product_type), unit = VALUES(unit), enabled = VALUES(enabled);

INSERT INTO erp_bom_line (product_id, material_id, quantity)
SELECT finished.id, board.id, 2.0000
FROM erp_product finished JOIN erp_product board ON board.code = 'RM-DEMO-001'
WHERE finished.code = 'FG-DEMO-001'
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity);

INSERT INTO erp_bom_line (product_id, material_id, quantity)
SELECT finished.id, screw.id, 4.0000
FROM erp_product finished JOIN erp_product screw ON screw.code = 'RM-DEMO-002'
WHERE finished.code = 'FG-DEMO-001'
ON DUPLICATE KEY UPDATE quantity = VALUES(quantity);

INSERT INTO inv_stock_balance (product_id, on_hand_quantity, reserved_quantity)
SELECT id, 30.0000, 0.0000 FROM erp_product WHERE code = 'RM-DEMO-001'
ON DUPLICATE KEY UPDATE product_id = VALUES(product_id);

INSERT INTO inv_stock_balance (product_id, on_hand_quantity, reserved_quantity)
SELECT id, 80.0000, 0.0000 FROM erp_product WHERE code = 'RM-DEMO-002'
ON DUPLICATE KEY UPDATE product_id = VALUES(product_id);

INSERT INTO inv_stock_transaction (product_id, transaction_type, quantity, reference_type, reference_id, reason, actor)
SELECT id, 'OPENING_RECEIPT', 30.0000, 'DEMO_SEED', 1, 'ForgeFlow 本地演示期初库存', 'demo-seed'
FROM erp_product p
WHERE p.code = 'RM-DEMO-001'
  AND NOT EXISTS (SELECT 1 FROM inv_stock_transaction t WHERE t.product_id = p.id AND t.reference_type = 'DEMO_SEED' AND t.reference_id = 1);

INSERT INTO inv_stock_transaction (product_id, transaction_type, quantity, reference_type, reference_id, reason, actor)
SELECT id, 'OPENING_RECEIPT', 80.0000, 'DEMO_SEED', 2, 'ForgeFlow 本地演示期初库存', 'demo-seed'
FROM erp_product p
WHERE p.code = 'RM-DEMO-002'
  AND NOT EXISTS (SELECT 1 FROM inv_stock_transaction t WHERE t.product_id = p.id AND t.reference_type = 'DEMO_SEED' AND t.reference_id = 2);
