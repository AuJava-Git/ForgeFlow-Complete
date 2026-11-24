# ForgeFlow 制造闭环扩展实施计划

> 按本计划实现一次性交付；步骤中的测试先于生产代码。

**目标：** 将现有 ERP→OA→MES 模块化单体扩展为“生产订单→备料/领料→MES 报工→质量检验→合格成品入库”，并仅在 BOM 物料短缺时生成采购单。

**架构：** 保持一个 Spring Boot 应用、一个 MySQL schema，沿用按业务包组织的模块化单体。订单创建时冻结 BOM 需求；库存余额与只追加流水在事务内更新；采购服务仅根据订单短缺量创建补料单；质检通过数量通过独立库存接口转成品库存。

**技术栈：** 延续 Java 17、Spring Boot 3.5.16、Spring Security/JWT、MyBatis-Plus、Flyway、MySQL 8、Maven Wrapper、Vue 3、Vite、Element Plus。

**依据：** 上传的 ForgeFlow 源码、`docs/forgeflow-mvp-spec.md` 与本次确认的制造闭环范围。

## 全局约束

- 不升级框架、不增加微服务、Redis、消息代理或其他新基础设施。
- 新增正式 Flyway 迁移从 V6 开始；本地演示迁移使用 V9，避开现有 dev V5。
- 订单物料需求以创建时 BOM 为准；旧订单不根据当前 BOM 静默补造快照。
- 只在短缺时采购；预留/发料不得部分成功；MES 未全量领料不得开工；只有质检通过数可入成品库存。
- 业务状态、库存余额、流水与采购/质检记录用服务事务保证一致；数量用 `BigDecimal` 和 4 位小数。
- 按测试先行实现；报告实际运行过的 Maven/npm 命令，Docker/MySQL 不可用时明确标注集成测试未运行。

## Review Focus

- BOM 在创建订单后变更：旧订单需求仍保持原快照（订单快照测试）。
- 多物料中一项短缺：预留事务整体失败，不留下部分预留；只能采购短缺量（库存/采购测试）。
- 订单无领料或重复领料：不能进入 MES，重复预留/发料不能重复记账（MES/库存测试）。
- 报工不存在、重复质检或质检数量与报工合格数不等：必须拒绝（质量测试）。
- 质检拒收或重复入库：拒收数量不得入成品库存，单次检验不得重复入账（质量/库存测试）。

---

### Task 1：订单 BOM 快照与数据库结构

**文件：** 修改 `ProductionOrderService`、`ProductionOrderView`、订单测试；新增 BOM 需求计算器及测试；新增正式迁移 V6。

**接口：** 创建订单时校验成品已启用且有 BOM，并以 `BOM 单耗 × 计划数` 保存 `erp_order_material_requirement`；创建后 BOM 修改不影响已创建订单。

- [x] 为计算精度、缺少 BOM、快照保真写失败测试并观察 RED。
- [x] 实现需求计算器、订单快照写入、物料状态字段和 V6 表结构。
- [x] 运行订单相关测试与既有回归测试。

### Task 2：库存余额、流水、备料与领料

**文件：** 新增 `inventory` 下的余额、需求快照 Mapper、流水实体/Mapper、服务、控制器、请求/响应 DTO 与单元测试；扩展认证权限。

**接口：** `GET /api/inventory/balances`、`POST /api/inventory/receipts`、`GET /api/inventory/production-orders/{id}/requirements`、`POST /api/inventory/production-orders/{id}/reserve`、`POST /api/inventory/production-orders/{id}/issue`。

- [x] 为短缺、全有或全无预留、重复发料、数量精度写失败测试并观察 RED。
- [x] 用 MySQL 行锁/条件更新保护余额；更新余额、订单物料状态与流水处于同一事务。
- [x] 运行库存服务与原有 MES/ERP 测试。

### Task 3：短缺采购及到货

**文件：** 新增 `purchasing` 订单/明细实体、Mapper、服务、控制器、请求/响应 DTO、单元测试；新增迁移 V7。

**接口：** `POST /api/purchasing/production-orders/{id}/shortage-orders` 仅按短缺量建单；`GET /api/purchasing/orders`；`POST /api/purchasing/orders/{id}/receipts` 部分/全部到货记入原料库存。

- [x] 测试无短缺不建采购单、重复建单被拒、收货不超过未到数量。
- [x] 实现采购单与库存收货的事务联动并运行采购/库存测试。

### Task 4：MES 备料前置与质量检验/成品入库

**文件：** 修改 `MesService`；新增 `quality` 检验实体、Mapper、服务、控制器、DTO 与测试；新增迁移 V8。

**接口：** MES 仅允许 `material_status=ISSUED` 的已批准订单开工；`GET /api/quality/pending`、`POST /api/quality/production-reports/{reportId}/inspect`、`POST /api/quality/inspections/{id}/finished-goods-receipt`。

- [x] 测试未领料不可开工、检验通过/拒收数量合计必须等于 MES 合格数、重复质检/入库被拒。
- [x] 只有检验通过数可入成品库存，库存流水关联检验记录；运行 MES/质量单元测试。

### Task 5：前端业务工作台

**文件：** 扩展 `frontend/src/api/client.js`、`auth/roles.js`、`router.js`；新增 Inventory、Purchasing、Quality 视图及前端测试。

**接口：** Planner/admin 可查库存、备料、补短缺；Operator 仅做 MES；Approver/admin 可做质量检验；三个工作台都调用真实 API，不写死库存/采购/检验数据。

- [x] 先为角色菜单/API 表单规则写测试并观察 RED。
- [x] 实现库存/采购/质检页面与加载、空状态、错误提示。
- [x] 运行 `npm test -- --run` 与 `npm run build`。

### Task 6：模拟数据、SQL、说明和交付验证

**文件：** 新增 `backend/src/main/resources/db/migration/V9__demo_manufacturing_data.sql`、独立 `database/forgeflow_simulation.sql`、架构与 Java 调用/注解/依赖说明；更新 README 和端到端演示脚本。

- [x] 准备原料、成品、BOM、期初原料库存，采购只在物料短缺时触发。
- [x] 说明完整目录、技术栈、每个 Java 文件职责/调用边、注解用途、Maven 依赖作用与作用域。
- [x] 运行后端全量测试、前端测试/构建、打包；在 Docker 不可用时如实标记 MySQL 集成验收未完成。
