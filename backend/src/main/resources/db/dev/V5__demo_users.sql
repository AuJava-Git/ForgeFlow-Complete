-- Local development demo identities only.
-- The BCrypt value is supplied by DEMO_PASSWORD_HASH; no plaintext password is stored here.
INSERT INTO sys_user (username, password_hash, display_name, role_code)
VALUES
    ('demo-admin', '${demo_password_hash}', '演示管理员', 'ADMIN'),
    ('demo-planner', '${demo_password_hash}', '演示计划员', 'PLANNER'),
    ('demo-approver', '${demo_password_hash}', '演示审批员', 'APPROVER'),
    ('demo-operator', '${demo_password_hash}', '演示操作员', 'OPERATOR');
