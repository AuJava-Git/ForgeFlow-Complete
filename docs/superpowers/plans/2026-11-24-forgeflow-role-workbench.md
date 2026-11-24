# ForgeFlow Role Workbench Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 ForgeFlow 的制造闭环演示升级为按岗位分工、按角色显示真实待办、带有多状态关联演示数据的中等复杂度协同应用。

**Architecture:** 保持一个 Spring Boot 模块化单体和现有业务 API；新增岗位角色的服务端鉴权及前端导航映射。首页按登录角色调用其已授权 API 生成待办表，演示数据只通过 `dev` profile 的 Flyway SQL 补充。

**Tech Stack:** Java 17、Spring Boot 3.5.16、Spring Security、MyBatis-Plus、Flyway、MySQL 8、Vue 3、Vite、Vitest、Element Plus。

**Spec:** `docs/forgeflow-role-workbench-spec.md`

## Global Constraints

- 不升级 Spring/Java 版本，不增加新的依赖、服务、数据库、缓存或消息基础设施。
- 保持 ForgeFlow 为制造闭环优先的模块化单体；采购仅按生产订单 BOM 短缺补料。
- 保留现有 API 的状态转换、事务和数量规则；角色权限必须在服务端强制执行。
- 开发演示用户密码只通过现有 `DEMO_PASSWORD_HASH` Flyway placeholder 提供，不写明文密码。
- 新增模拟业务记录使用 `dev` profile SQL；迁移需兼容 V1–V9 已存在的本地开发数据库。
- 先写测试、观察预期失败后再写生产代码；最终报告实际运行过的命令和 MySQL 集成测试状态。

## Review Focus

- 未知/错误角色访问路由和 API：必须拒绝，覆盖角色映射与服务端授权测试。
- 岗位首页不得请求该角色无权限的 API，且显示数量和明细必须来自接口数据，覆盖 dashboard helper 测试。
- 各岗位待办定义不能误报：只列草稿计划、待批审批、待备/领料订单、未到货补料行、可执行 MES 订单、待检报工，覆盖队列规则测试。
- 种子订单的状态、库存需求快照、预留/领料、报工、检验和入库数量必须闭合，覆盖 MySQL 种子集成测试。
- 采购种子数量不得高于关联订单短缺量，覆盖 SQL 集成断言及现有采购服务测试。

---

### Task 1: 扩展岗位角色与服务端授权

**Files:**
- Create: `backend/src/main/resources/db/migration/V10__job_role_roles.sql` to replace `chk_sys_user_role` without rewriting an already-applied migration.
- Modify: `backend/src/main/java/com/forgeflow/auth/SecurityConfig.java`
- Test: `backend/src/test/java/com/forgeflow/auth/RoleAuthorizationTest.java`

**Interfaces:**
- Consumes: Existing role codes `ADMIN`, `PLANNER`, `APPROVER`, `OPERATOR`.
- Produces: Allowed role codes also include `WAREHOUSE`, `PURCHASER`, `QUALITY_INSPECTOR`. Product/order planning remains for `ADMIN`/`PLANNER`; inventory for `ADMIN`/`WAREHOUSE`; shortfall purchasing for `ADMIN`/`PURCHASER`; approval for `ADMIN`/`APPROVER`; MES for `ADMIN`/`OPERATOR`; quality for `ADMIN`/`QUALITY_INSPECTOR`. All authenticated roles may read the existing product/order APIs needed by their authorized work pages.

- [x] **Step 1: Write failing authorization tests** for the role-to-module matrix, specifically that each role can reach only its assigned business module and that a planner cannot create a purchase order, an approver cannot inspect quality, and a purchaser cannot reserve inventory.
- [x] **Step 2: Run `./mvnw -Dtest=RoleAuthorizationTest test`** and confirm the expected missing-role/authorization assertions fail.
- [x] **Step 3: Add the V10 role check-constraint migration and update `SecurityConfig` matchers** to match the exact matrix in the Interfaces block; keep existing authenticated read routes as required for the work pages.
- [x] **Step 4: Run `./mvnw -Dtest=RoleAuthorizationTest test` and `./mvnw test`**; confirm both pass.

### Task 2: 前端岗位导航和角色体验

**Files:**
- Modify: `frontend/src/auth/roles.js`
- Modify: `frontend/src/components/AppShell.vue`
- Test: `frontend/src/__tests__/route-roles.test.js`
- Test: `frontend/src/__tests__/route-guard.test.js`

**Interfaces:**
- Consumes: Backend role names from Task 1.
- Produces: `navigationForRole(role)` shows only the modules assigned to that role; existing route guard denies all other business routes. Role labels are 计划员、审批员、仓库员、采购员、生产操作员、质检员、系统管理员.

- [x] **Step 1: Add role-navigation tests** asserting the exact visible route keys for all seven roles and denial for unknown roles.
- [x] **Step 2: Run `npm test -- --run src/__tests__/route-roles.test.js src/__tests__/route-guard.test.js`** and confirm the new-role cases fail.
- [x] **Step 3: Update role access, navigation labels and the AppShell role-label map** without changing the existing route names or module APIs.
- [x] **Step 4: Run the targeted Vitest files and `npm test -- --run`**; confirm all pass.

### Task 3: 按岗位生成真实待办工作台

**Files:**
- Create: `frontend/src/dashboard/workbench.js`
- Modify: `frontend/src/views/DashboardView.vue`
- Test: `frontend/src/__tests__/workbench.test.js`

**Interfaces:**
- Consumes: Existing API functions `orders`, `pendingApprovals`, `purchaseOrders`, `workOrders`, `qualityPending` and authenticated role from `readSession()`.
- Produces: `loadWorkQueues(role, api)` calls only API sources authorized for that role and returns one or more queue sections containing `title`, `count`, `rows`, `path`, and an action label. Queue rules: PLANNER—`DRAFT` orders; APPROVER—pending approval rows; WAREHOUSE—approved orders whose material status is not `ISSUED`; PURCHASER—purchase lines with positive outstanding quantity; OPERATOR—approved/in-progress work orders; QUALITY_INSPECTOR—pending quality rows; ADMIN—all sections.

- [x] **Step 1: Write unit tests** for the API-source allowlist, each role's queue filtering, multi-line purchase orders, empty queues, and ADMIN aggregation.
- [x] **Step 2: Run `npm test -- --run src/__tests__/workbench.test.js`** and confirm the helper is missing or fails the queue assertions.
- [x] **Step 3: Implement `loadWorkQueues(role, api)`** as a small role-to-existing-API adapter and render the resulting real task rows in a “我的待办” table in `DashboardView.vue`; retain the existing live dashboard summary and add links into existing module pages.
- [x] **Step 4: Run targeted workbench tests, `npm test -- --run`, and `npm run build`**; confirm the queue tests, full frontend suite and build pass.

### Task 4: 丰富演示主数据与业务闭环记录

**Files:**
- Create: `backend/src/main/resources/db/dev/V11__demo_workbench_data.sql`
- Modify: `README.md`
- Modify: `docs/demo-script.md`
- Test: `backend/src/test/java/com/forgeflow/seed/DemoWorkbenchSeedIT.java`

**Interfaces:**
- Consumes: Existing V1–V10 schema, V5/V9 development seed, role/password Flyway placeholders and all existing business tables.
- Produces: Development demo data totals 4 finished products and 6 raw materials, multiple BOMs, all six production-order statuses, and linked warehouse, shortage-purchasing, MES, quality and finished-goods records. Add `demo-warehouse`, `demo-purchaser`, and `demo-quality` using `${demo_password_hash}`. Existing demo accounts remain valid.

- [x] **Step 1: Write a MySQL Testcontainers integration test** that starts the app with `dev` profile, then asserts the full catalog counts, all six order statuses, at least one live task for every non-admin queue, linked production/report/inspection/receipt quantities, and that any seeded purchase line equals the associated shortage.
- [ ] **Step 2: Run `./mvnw -Pmysql-it -Dit.test=DemoWorkbenchSeedIT verify`** and confirm the assertions fail against the current minimal V9 seed. Blocked: Sandbox has no usable Docker environment, so Testcontainers cannot start.
- [x] **Step 3: Add V11 development SQL** for role demo users and consistent workflow rows, using natural business keys and `NOT EXISTS`/upsert patterns so the seed does not reset mutable inventory or duplicate business records; update README role table and the demo script to show one meaningful action per role.
- [ ] **Step 4: Run `./mvnw -Pmysql-it -Dit.test=DemoWorkbenchSeedIT verify`**, then the full `./mvnw verify`; record explicitly if Docker/MySQL is unavailable. Backend `verify` passed; MySQL integration remains blocked by Docker availability.

### Task 5: 全量回归与交付核对

**Files:**
- Review: all files above plus `docs/forgeflow-role-workbench-spec.md` and this plan.

- [x] **Step 1: Run the full backend suite:** `./mvnw verify` (34 tests passed).
- [x] **Step 2: Run the full frontend suite and production build:** `npm test -- --run && npm run build` from `frontend/` (26 tests passed; production build passed).
- [x] **Step 3: Check requirement coverage:** confirm role routes and service permissions agree, each workbench shows live role-appropriate tasks, seed data covers each queue, and procurement remains shortage-only.
- [x] **Step 4: Review `git diff --check` when a Git worktree is available; otherwise inspect the changed-file list and whitespace manually. Report any unavailable MySQL integration run without claiming it passed.** Manually reviewed changed SQL/docs; this folder is not a Git worktree. MySQL integration status remains unverified because Docker is unavailable here.
