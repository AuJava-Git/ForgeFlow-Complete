# ForgeFlow 智造协同平台

ForgeFlow 是一个制造协同作品集项目。它采用**模块化单体**架构：一个 Spring Boot 服务和一个 MySQL 8 数据库，在同一应用内串联 ERP、OA、库存、补料采购、MES 与质量检验，没有拆成微服务。

## 制造闭环

1. **ERP**：维护产品与 BOM，创建生产订单；创建时按 BOM 冻结物料需求快照。
2. **OA**：审批员批准或驳回生产订单并留下历史记录。
3. **库存**：按订单需求核对库存，只有所有原料都充足才允许整单预留；预留完成后确认领料。
4. **采购**：发现短缺时只按缺口生成关联订单的补料采购单；到货支持分批登记，且不能超过未到数量。
5. **MES**：物料已领用后才能开工；操作员分批报工，合格与不合格累计不能超过计划。
6. **质量**：检验 MES 合格报工批次；检验通过数量可以转入成品库存，每条检验最多入库一次。

## 技术栈与项目说明

- 后端：Java 17、Spring Boot 3.5、Spring Security/JWT、MyBatis-Plus、Flyway、MySQL 8、Maven Wrapper。
- 前端：Vue 3、Vue Router、Vite、Element Plus、Axios。
- 详细 Java 文件调用关系、注解用途、依赖的作用和范围见[架构与实现说明](docs/architecture-and-implementation.md)。
- 端到端演示步骤见[演示脚本](docs/demo-script.md)。
- 独立的模拟业务数据 SQL：[`database/forgeflow_simulation.sql`](database/forgeflow_simulation.sql)。

## 本地启动

### 先决条件

- JDK 17 或更高版本。
- Node.js 22.12 或更高版本（Vitest 5 要求）。
- Docker Engine 与 Docker Compose v2，用于本地 MySQL。

### 1. 配置环境

在项目根目录复制环境变量模板：

```bash
cp .env.example .env
```

设置 `MYSQL_PASSWORD` 与 `SPRING_DATASOURCE_PASSWORD` 为相同数据库口令，为 `MYSQL_ROOT_PASSWORD` 设置本机 root 口令，并将 `APP_JWT_SECRET` 替换为 `openssl rand -base64 32` 的输出。若调整 `MYSQL_PORT`，同步调整 `SPRING_DATASOURCE_URL`。不要提交 `.env`；公开演示账号和开发密钥不得用于生产部署。

`DEMO_PASSWORD_HASH` 中的 `$` 在 `.env` 里写作 `\u0024`，避免 Docker Compose 将其误认为环境变量；Spring Boot 按 `.properties` 读取时会还原为 `$`。

### 2. 启动数据库与后端

```bash
docker compose up -d mysql
docker compose ps
./mvnw -Dspring-boot.run.profiles=dev spring-boot:run
```

`dev` profile 会自动执行岗位演示用户和多状态制造种子：4 种成品、6 种原料、多项 BOM，以及覆盖草稿、审批、缺料补购、生产、待检和完工入库的订单。采购数据只补生产短缺。

若本机先前以 `default` profile 建过 V1–V8，`dev` profile 会按需补跑 V5/V9 演示数据；演示迁移不会覆盖你后续手工改过的库存余额。Cursor 的 Run and Debug 可直接选 `ForgeFlowApplication (dev)`，启动配置会使用项目根目录读取 `.env`。

健康检查：

```bash
curl http://localhost:8080/api/health
```

预期返回 `{"status":"UP"}`。

如果数据库已执行 Flyway V1–V8，且只想插入模拟制造数据，也可手动执行 `database/forgeflow_simulation.sql`。使用 `dev` profile 时 V9 已执行，不需要重复运行。

### 3. 启动前端

新开终端：

```bash
cd frontend
npm ci
npm run dev
```

打开 <http://localhost:5173>，按[端到端演示脚本](docs/demo-script.md)从订单走到成品入库。Vite 会将 `/api` 请求代理到本地 Spring Boot 服务。

## 本地演示账号

七个账号共用口令 `ForgeFlowDemo2026!`，只用于 `dev` profile 的本机演示。

| 用户名 | 角色 | 菜单权限摘要 |
|---|---|---|
| `demo-admin` | 管理员 | 全部模块 |
| `demo-planner` | 计划员 | 产品/BOM、创建并提交生产订单 |
| `demo-approver` | 审批员 | OA 审批与驳回，保留审批历史 |
| `demo-warehouse` | 仓库员 | 原料库存、按订单备料与领料 |
| `demo-purchaser` | 采购员 | 仅处理生产短缺补料单与到货登记 |
| `demo-operator` | 生产操作员 | MES 开工与分批报工 |
| `demo-quality` | 质检员 | 检验 MES 合格报工并办理成品入库 |

权限同时由后端 Spring Security 和前端路由守卫执行；前端隐藏菜单不是后端授权的替代。

## 验证

```bash
./mvnw test
cd frontend
npm test -- --run
npm run build
```

如需运行 MySQL/Testcontainers 集成测试（需要可用的 Docker）：

```bash
./mvnw -Pmysql-it -Dit.test=DemoWorkbenchSeedIT verify
```

当前交付环境无法运行 Docker/MySQL，因而已运行的后端单元测试不等同于数据库迁移或真实数据库集成验证。请在启用 Docker 的机器上执行上述集成测试。全量 npm 审计仍有 4 项 moderate，位于开发/测试工具传递依赖；生产依赖 audit 为 0，具体见[依赖审计记录](docs/frontend-dependency-audit.md)。

## 范围边界

这是用于学习和演示的 MVP，不是可直接用于生产的 ERP/MES/QMS。没有供应商管理、财务成本、采购审批、库位/批次、条码、返工或多级质检等能力。请勿在生产环境使用演示口令或示例密钥。
