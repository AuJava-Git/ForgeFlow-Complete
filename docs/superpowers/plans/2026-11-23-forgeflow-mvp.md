# ForgeFlow MVP Implementation Plan

> **For agentic workers:** Implement task-by-task from this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a runnable modular-monolith manufacturing workflow that connects ERP production orders, OA approval, and MES reporting in one demonstrable end-to-end slice.

**Architecture:** One Spring Boot application and one MySQL schema, organized by business feature. ERP production orders are the shared workflow record; OA actions and MES reports are append-only records, and each state-changing operation updates the order transactionally. Vue 3 calls REST endpoints through the Vite `/api` proxy.

**Tech Stack:** Java 17 target, Spring Boot 3.x, Spring Security, MyBatis-Plus, Flyway, MySQL 8, Maven Wrapper, Vue 3, Vite, Element Plus, Docker Compose.

**Spec:** `/workspace/docs/forgeflow-mvp-spec.md`

## Global Constraints

- Keep one deployable Spring Boot application and one MySQL database; package by feature, not microservice.
- Implement only the ERP product/BOM/production-order → OA approval → MES production-reporting workflow in the MVP.
- Do not add Redis, a broker, another database, or a distributed workflow.
- Keep credentials and JWT signing keys out of source control; seed demo users only for local development.
- Write a failing test before production behavior; run the focused test to observe the expected failure, then implement the minimum change and run the full available suite.
- Current environment has JDK 21 and Node/npm but no Maven, Docker, or local MySQL server; bootstrap Maven Wrapper. Keep MySQL integration verification separate and report it as blocked until a Docker/MySQL environment is available.
- Ignore the pre-existing `/workspace/skill-import/` directory in Git; do not stage or modify its contents.

## Review Focus

- Duplicate product/material codes or missing/disabled products must not create a valid production order — test in Task 3.
- Only a pending order may be approved/rejected, and only the approver/admin role may decide — test in Task 4.
- MES must reject a report that has no positive quantity or would exceed the remaining plan — test in Task 5.
- A role must not perform another role's protected operation even if it calls the API directly — test in Task 2 and Task 4.
- Failed report writes must not leave report details and order totals inconsistent — test transactional behavior in Task 5's MySQL integration profile; run only when MySQL is available.

---

### Task 1: Bootstrap the monolith and health endpoint

**Files:**
- Create: `pom.xml`, `.mvn/wrapper/*`, `mvnw`, `mvnw.cmd`
- Create: `backend/src/main/java/com/forgeflow/ForgeFlowApplication.java`
- Create: `backend/src/main/java/com/forgeflow/platform/health/HealthController.java`
- Test: `backend/src/test/java/com/forgeflow/platform/health/HealthControllerTest.java`
- Create: `.gitignore`, `.env.example`, `docker-compose.yml`

**Interfaces:**
- Produces: `GET /api/health` returns HTTP 200 and JSON `{"status":"UP"}` without connecting to MySQL.

- [ ] **Step 1: Initialize the project repository and Maven Wrapper**

  Initialize Git at `/workspace`; put `skill-import/`, build outputs, `.env`, and IDE files in `.gitignore`. Generate Maven Wrapper and configure the root `pom.xml` to use `backend/src/main/java`, `backend/src/test/java`, and `backend/src/main/resources`, Spring Boot 3.x dependency management, Java 17 target, and JUnit/Spring test dependencies.
- [ ] **Step 2: Write the failing health test**

  `HealthControllerTest.healthEndpointReturnsUp` sends `GET /api/health` and asserts status `200` and JSON path `$.status == "UP"`. The test must use a web slice and not require MySQL.
- [ ] **Step 3: Run the test and verify the expected failure**

  Run: `./mvnw -Dtest=HealthControllerTest test`

  Expected: the test fails because the health route is not implemented, not because dependency resolution or test setup failed.
- [ ] **Step 4: Implement the minimal application and health controller**

  Keep the health route public and independent of datasource initialization. Add no business logic in this task.
- [ ] **Step 5: Add the local MySQL Compose configuration and verify the test suite**

  Compose exposes MySQL 8 on a configurable host port and reads password values from environment variables; do not add credentials to YAML. Run: `./mvnw test`. Expected: all current tests pass. Compose startup is not claimed in the current environment.
- [ ] **Step 6: Commit the verified foundation**

  Run: `git add .gitignore .env.example docker-compose.yml pom.xml .mvn mvnw mvnw.cmd backend && git commit -m "chore: bootstrap ForgeFlow monolith"`.

### Task 2: Add demo identity, login, and role authorization

**Files:**
- Create: `backend/src/main/java/com/forgeflow/auth/AuthController.java`
- Create: `backend/src/main/java/com/forgeflow/auth/AuthService.java`
- Create: `backend/src/main/java/com/forgeflow/auth/SecurityConfig.java`
- Create: `backend/src/main/java/com/forgeflow/auth/UserMapper.java`
- Create: `backend/src/main/java/com/forgeflow/auth/LoginRequest.java`
- Create: `backend/src/main/java/com/forgeflow/auth/LoginResponse.java`
- Create: `backend/src/main/resources/db/migration/V1__identity.sql`
- Test: `backend/src/test/java/com/forgeflow/auth/AuthServiceTest.java`
- Test: `backend/src/test/java/com/forgeflow/auth/RoleAuthorizationTest.java`

**Interfaces:**
- Produces: `POST /api/auth/login` accepts `{username,password}` and returns a signed bearer token plus role; protected routes require authorities `ADMIN`, `PLANNER`, `APPROVER`, or `OPERATOR`.
- Produces: `AuthService.login(String username, String password): LoginResponse`.

- [ ] **Step 1: Write failing login and authentication-boundary tests**

  `AuthServiceTest.rejectsInvalidCredentials` asserts invalid credentials are rejected. `AuthServiceTest.issuesBearerTokenWithRoleAndExpiry` asserts a valid authenticated principal produces a signed token with the expected role and expiry. `RoleAuthorizationTest.anonymousRequestToBusinessApiIsUnauthorized` requests `/api/erp/products` without a bearer token and asserts `401`.
- [ ] **Step 2: Run focused tests and verify expected failures**

  Run: `./mvnw -Dtest=AuthServiceTest,RoleAuthorizationTest test`

  Expected: missing login/token/security behavior causes the failures; the tests must not require a running MySQL instance.
- [ ] **Step 3: Implement authentication and role rules**

  Store BCrypt password hashes and role codes in `sys_user`; issue and verify short-lived signed JWTs using Spring Security's resource-server support and a signing key loaded from an environment variable. Define public access only for health and login. Keep demo-user seed data for Task 8's local profile; never place signing keys or production passwords in source.
- [ ] **Step 4: Verify authentication tests and all existing tests**

  Run: `./mvnw test`. Expected: invalid credentials fail, valid login returns a role-bearing token, anonymous business API access returns `401`, and previous tests pass.
- [ ] **Step 5: Commit the verified identity feature**

  Run: `git add backend && git commit -m "feat: add role-based authentication"`.

### Task 3: Implement ERP product/BOM and production-order creation

**Files:**
- Create: `backend/src/main/java/com/forgeflow/erp/product/ProductController.java`
- Create: `backend/src/main/java/com/forgeflow/erp/product/ProductService.java`
- Create: `backend/src/main/java/com/forgeflow/erp/product/ProductMapper.java`
- Create: `backend/src/main/java/com/forgeflow/erp/product/ProductRequest.java`
- Create: `backend/src/main/java/com/forgeflow/erp/product/BomRequest.java`
- Create: `backend/src/main/java/com/forgeflow/erp/order/ProductionOrderController.java`
- Create: `backend/src/main/java/com/forgeflow/erp/order/ProductionOrderService.java`
- Create: `backend/src/main/java/com/forgeflow/erp/order/ProductionOrderMapper.java`
- Create: `backend/src/main/java/com/forgeflow/erp/order/ProductionOrderRequest.java`
- Create: `backend/src/main/resources/db/migration/V2__erp_products_and_orders.sql`
- Test: `backend/src/test/java/com/forgeflow/erp/product/ProductServiceTest.java`
- Test: `backend/src/test/java/com/forgeflow/erp/order/ProductionOrderServiceTest.java`

**Interfaces:**
- Produces: `POST/GET /api/erp/products`, `PUT /api/erp/products/{id}/bom`, `POST/GET /api/erp/production-orders`.
- Produces: `ProductionOrderService.create(ProductionOrderRequest request, String actor): ProductionOrderView`; new orders have a unique number and status `DRAFT`.

- [ ] **Step 1: Write failing product and order tests**

  `ProductServiceTest.rejectsDuplicateCode` asserts a duplicate product code is rejected. `ProductionOrderServiceTest.rejectsNonPositivePlannedQuantity` asserts zero and negative planned quantities are rejected. `ProductionOrderServiceTest.createsDraftOrderForEnabledFinishedProduct` asserts an enabled finished product creates a `DRAFT` order with the requested positive quantity.
- [ ] **Step 2: Run focused tests to observe expected failures**

  Run: `./mvnw -Dtest=ProductServiceTest,ProductionOrderServiceTest test`. Expected: tests fail because ERP services and validation rules are missing.
- [ ] **Step 3: Implement ERP DTOs, services, MyBatis-Plus mappers, and Flyway schema**

  Enforce unique product codes, valid product types, positive BOM quantities, active raw-material references, enabled finished-product selection, and positive integer order quantity. Use request/response DTOs rather than returning persistence entities. Restrict writes to `ADMIN` and `PLANNER`.
- [ ] **Step 4: Verify ERP tests and full unit suite**

  Run: `./mvnw test`. Expected: duplicate codes and invalid order quantities are rejected; valid requests produce a `DRAFT` order.
- [ ] **Step 5: Commit the verified ERP feature**

  Run: `git add backend && git commit -m "feat: add ERP products BOM and production orders"`.

### Task 4: Add OA submission and approval history

**Files:**
- Create: `backend/src/main/java/com/forgeflow/oa/ApprovalController.java`
- Create: `backend/src/main/java/com/forgeflow/oa/ApprovalService.java`
- Create: `backend/src/main/java/com/forgeflow/oa/ApprovalMapper.java`
- Create: `backend/src/main/java/com/forgeflow/oa/ApprovalActionRequest.java`
- Create: `backend/src/main/resources/db/migration/V3__oa_approval_actions.sql`
- Test: `backend/src/test/java/com/forgeflow/oa/ApprovalServiceTest.java`
- Test: `backend/src/test/java/com/forgeflow/oa/ApprovalAuthorizationTest.java`

**Interfaces:**
- Produces: `POST /api/oa/production-orders/{id}/submit`, `POST /api/oa/production-orders/{id}/decision`, `GET /api/oa/production-orders/pending`, and `GET /api/oa/production-orders/{id}/history`.
- Produces: `ApprovalService.submit(long orderId, String actor)` and `ApprovalService.decide(long orderId, boolean approved, String comment, String actor)`; status changes and action records commit atomically.

- [ ] **Step 1: Write failing approval state and permission tests**

  `ApprovalServiceTest.approvesOnlyPendingOrder` asserts a pending order becomes `APPROVED` and creates an approval history entry. `ApprovalServiceTest.rejectsDecisionForNonPendingOrder` asserts a repeated decision fails without adding history. `ApprovalAuthorizationTest.plannerCannotDecideApproval` asserts `403` for a planner calling the decision endpoint.
- [ ] **Step 2: Run focused tests and verify failures are caused by absent OA behavior**

  Run: `./mvnw -Dtest=ApprovalServiceTest,ApprovalAuthorizationTest test`. Expected: missing OA transition/authorization behavior causes the focused failures.
- [ ] **Step 3: Implement submit/decision rules and append-only action history**

  Submitting is allowed only from `DRAFT`; it changes the order to `PENDING_APPROVAL` and records `SUBMIT`. An approver/admin decision is allowed only from `PENDING_APPROVAL`, records `APPROVE` or `REJECT` with actor/comment/time, and moves the order to `APPROVED` or `REJECTED`. Reject resubmission and duplicate decisions.
- [ ] **Step 4: Verify OA tests and full unit suite**

  Run: `./mvnw test`. Expected: valid transition and history assertions pass; repeated/invalid transitions and planner approval are rejected.
- [ ] **Step 5: Commit the verified OA feature**

  Run: `git add backend && git commit -m "feat: add OA approval workflow"`.

### Task 5: Implement MES start, reporting, and completion rules

**Files:**
- Create: `backend/src/main/java/com/forgeflow/mes/MesController.java`
- Create: `backend/src/main/java/com/forgeflow/mes/MesService.java`
- Create: `backend/src/main/java/com/forgeflow/mes/MesReportMapper.java`
- Create: `backend/src/main/java/com/forgeflow/mes/ProductionReportRequest.java`
- Create: `backend/src/main/java/com/forgeflow/mes/ProductionReportView.java`
- Create: `backend/src/main/resources/db/migration/V4__mes_production_reports.sql`
- Test: `backend/src/test/java/com/forgeflow/mes/MesServiceTest.java`
- Test: `backend/src/test/java/com/forgeflow/mes/MesTransactionIT.java`

**Interfaces:**
- Produces: `GET /api/mes/work-orders`, `POST /api/mes/work-orders/{orderId}/start`, `POST /api/mes/work-orders/{orderId}/reports`.
- Produces: `MesService.report(long orderId, ProductionReportRequest request, String actor): ProductionReportView`.

- [ ] **Step 1: Write failing MES behavior tests**

  `MesServiceTest.rejectsReportWithNoPositiveQuantity` asserts zero/zero is rejected. `MesServiceTest.rejectsReportAboveRemainingPlan` asserts a report whose total exceeds remaining planned quantity is rejected. `MesServiceTest.completesOrderWhenReportedTotalReachesPlan` asserts a valid final report moves an `IN_PROGRESS` order to `COMPLETED` and preserves good/defect totals.
- [ ] **Step 2: Run the focused tests and verify expected failures**

  Run: `./mvnw -Dtest=MesServiceTest test`. Expected: MES service behavior is missing.
- [ ] **Step 3: Implement MES start/report behavior and transactional updates**

  Permit start only from `APPROVED`; permit reports only from `IN_PROGRESS`; require nonnegative integer good/defect counts with a positive combined count; reject totals above the plan. Insert each report and update cumulative counts/status in one transaction. Add a Failsafe `mysql-it` profile for the opt-in integration test; do not require Docker for the default unit-test suite.
- [ ] **Step 4: Verify unit tests and opt-in MySQL transaction test when infrastructure exists**

  Run: `./mvnw test`; when Docker/MySQL is available, run `./mvnw -Pmysql-it verify` and verify a forced report persistence failure leaves both detail and totals unchanged. In the current environment, record this integration check as blocked rather than passed.
- [ ] **Step 5: Commit the verified MES feature**

  Run: `git add backend pom.xml && git commit -m "feat: add MES reporting workflow"`.

### Task 6: Add a real dashboard summary API

**Files:**
- Create: `backend/src/main/java/com/forgeflow/dashboard/DashboardController.java`
- Create: `backend/src/main/java/com/forgeflow/dashboard/DashboardService.java`
- Create: `backend/src/main/java/com/forgeflow/dashboard/DashboardMapper.java`
- Create: `backend/src/main/java/com/forgeflow/dashboard/DashboardSummary.java`
- Test: `backend/src/test/java/com/forgeflow/dashboard/DashboardServiceTest.java`

**Interfaces:**
- Produces: `GET /api/dashboard/summary` returns `totalOrders`, `pendingApproval`, `inProgress`, `completed`, `goodQuantity`, and `defectQuantity`; only authenticated users may access it.

- [ ] **Step 1: Write a failing dashboard aggregation test**

  `DashboardServiceTest.summarizesOrderStatesAndReportTotals` asserts the service returns the six specified fields from mapper aggregates, including zero counts for empty states.
- [ ] **Step 2: Run the focused test to verify it fails because the summary service is missing**

  Run: `./mvnw -Dtest=DashboardServiceTest test`.
- [ ] **Step 3: Implement the dashboard query and response DTO**

  Keep aggregation read-only and use mapper aggregate queries; do not hard-code demo counts or add another persistence technology.
- [ ] **Step 4: Run all backend unit tests**

  Run: `./mvnw test`. Expected: dashboard aggregate assertions and all previous tests pass.
- [ ] **Step 5: Commit the verified dashboard API**

  Run: `git add backend && git commit -m "feat: add production dashboard summary"`.

### Task 7: Build the Vue workflow screens

**Files:**
- Create: `frontend/package.json`, `frontend/vite.config.js`, `frontend/index.html`
- Create: `frontend/src/main.js`, `frontend/src/router.js`, `frontend/src/api/client.js`
- Create: `frontend/src/views/LoginView.vue`
- Create: `frontend/src/views/DashboardView.vue`
- Create: `frontend/src/views/ProductsView.vue`
- Create: `frontend/src/views/ProductionOrdersView.vue`
- Create: `frontend/src/views/ApprovalView.vue`
- Create: `frontend/src/views/MesWorkbenchView.vue`
- Create: `frontend/src/components/AppShell.vue`
- Test: `frontend/src/__tests__/route-roles.test.js`
- Test: `frontend/src/__tests__/report-form.test.js`

**Interfaces:**
- Consumes: the API routes defined in Tasks 2–6 through `frontend/src/api/client.js`.
- Produces: role-aware navigation and forms for the complete workflow; dashboard cards reflect API results, not hard-coded counters.

- [ ] **Step 1: Scaffold Vite and write failing front-end tests**

  Configure Vue 3, Element Plus, Vitest, and Vue Test Utils. `route-roles.test.js` asserts an operator cannot see the approval route; `report-form.test.js` asserts zero/zero report input is rejected and valid counts are accepted.
- [ ] **Step 2: Run the tests and verify the expected failures**

  Run: `cd frontend && npm test -- --run`. Expected: role/form behaviors are not implemented.
- [ ] **Step 3: Implement login, ERP, OA, MES, and dashboard views**

  Use Vite proxy `/api` for local development and keep the bearer token in `sessionStorage`. Show server validation errors near the relevant form and include loading, empty, and error states. Do not add unrelated pages.
- [ ] **Step 4: Verify front-end tests and production build**

  Run: `cd frontend && npm test -- --run && npm run build`. Expected: role/form tests pass and Vite emits a production build.
- [ ] **Step 5: Commit the verified frontend**

  Run: `git add frontend && git commit -m "feat: add ForgeFlow workflow UI"`.

### Task 8: Add reproducible demo data, runbook, and final verification

**Files:**
- Create: `backend/src/main/resources/db/dev/V1__demo_users.sql`
- Create: `README.md`
- Create: `docs/demo-script.md`
- Modify: `.env.example`, `docker-compose.yml` only if needed

**Interfaces:**
- Produces: a documented demo path from login through completed production order, plus exact local setup and verification commands.

- [ ] **Step 1: Write the business smoke-test checklist**

  `docs/demo-script.md` lists seeded product/BOM data, demo role names, order quantity, expected status after each action, and an example split report whose final sum reaches the plan.
- [ ] **Step 2: Add local-only demo users and configuration**

  Put demo users behind the `dev` profile, use BCrypt hashes, and document that demo credentials/signing key are for local use only. Production profile must not auto-create demo identities.
- [ ] **Step 3: Write the runbook and accurate project status**

  README documents prerequisites, environment variables, database setup, start commands, demo users, test commands, architecture boundaries, and explicit non-goals. Mark MySQL integration as unverified if the environment still lacks Docker/MySQL.
- [ ] **Step 4: Run every locally available verification command**

  Run: `./mvnw test`; `cd frontend && npm test -- --run && npm run build`; run `./mvnw -Pmysql-it verify` only after MySQL/Docker is available. Capture actual output before claiming success.
- [ ] **Step 5: Review and commit the complete runbook**

  Confirm no secret values or real user data are committed, README claims correspond to implemented/tested behavior, and the final available test/build commands have fresh passing output. Run `git add README.md docs backend/src/main/resources/db/dev .env.example docker-compose.yml && git commit -m "docs: add ForgeFlow demo runbook"`.

Do not claim a full ERP/MES/OA system; describe the delivered vertical slice precisely.
