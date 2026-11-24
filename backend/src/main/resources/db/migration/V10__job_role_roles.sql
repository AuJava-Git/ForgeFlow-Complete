ALTER TABLE sys_user DROP CHECK chk_sys_user_role;

ALTER TABLE sys_user
    ADD CONSTRAINT chk_sys_user_role
    CHECK (role_code IN ('ADMIN', 'PLANNER', 'APPROVER', 'WAREHOUSE', 'PURCHASER', 'OPERATOR', 'QUALITY_INSPECTOR'));
