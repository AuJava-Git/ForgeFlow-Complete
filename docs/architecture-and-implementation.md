# ForgeFlow 制造闭环：架构、调用关系与实现说明

## 先看整体

ForgeFlow 是一个**模块化单体**：一个 Spring Boot 应用、一个 MySQL 8 数据库，ERP、OA、库存、采购、MES、质量模块在同一进程内通过 Spring 注入彼此调用。没有拆微服务，也没有引入消息队列、缓存或独立库存服务。

端到端业务顺序为：

```text
产品 / BOM
  → 生产订单（冻结物料需求快照）
  → OA 审批
  → 库存短缺检查 / 整单备料预留 / 领料
  → MES 开工 / 分批报工
  → 质量检验
  → 质检通过数量进入成品库存
```

只有已批准订单可以备料和领料；只有物料状态为 `ISSUED` 的订单可以在 MES 开工。库存不足时，采购模块只按短缺量创建一张关联该生产订单的补料采购单；到货可以分批登记，但不能超过未到数量。

## 技术栈

| 层 | 技术 |
|---|---|
| 后端语言与框架 | Java 17、Spring Boot 3.5.16 |
| HTTP/API | Spring MVC、Jackson JSON |
| 身份与权限 | Spring Security、OAuth2 Resource Server 的 Bearer JWT 支持、Nimbus JWT、BCrypt |
| 数据访问 | MyBatis-Plus 3.5.12、MyBatis 注解 SQL |
| 数据库与迁移 | MySQL 8、MySQL Connector/J、Flyway |
| 前端 | Vue 3、Vue Router 4、Vite 6、Element Plus、Axios |
| 验证 | JUnit 5 / Spring Test / Mockito；Vitest、Vue Test Utils、jsdom；Testcontainers MySQL（集成测试） |

当前表结构由 Flyway 管理；没有 JPA 实体管理器、Redis、Kafka/RabbitMQ 或微服务间 HTTP/RPC 调用。

## 依赖及作用范围

### Maven（根目录 `pom.xml`）

| 依赖 | Scope | 用途 |
|---|---|---|
| `spring-boot-starter-web` | 默认 compile | Spring MVC REST 控制器、JSON 序列化和嵌入式 Web 服务器 |
| `spring-boot-starter-validation` | 默认 compile | Jakarta Bean Validation；校验请求 DTO 的必填、长度、范围和格式 |
| `spring-boot-starter-security` | 默认 compile | 安全过滤链、认证授权与密码编码器 |
| `spring-boot-starter-oauth2-resource-server` | 默认 compile | 校验请求中的 Bearer JWT，并转换 JWT 权限声明为 Spring authorities |
| `mybatis-plus-spring-boot3-starter` 3.5.12 | 默认 compile | MyBatis / MyBatis-Plus 映射器、实体映射、条件构造器和 Spring Boot 集成 |
| `flyway-core` + `flyway-mysql` | 默认 compile | 按版本执行 schema 迁移；MySQL 插件提供 MySQL 数据库支持 |
| `mysql-connector-j` | runtime | 应用运行时连接 MySQL 8 |
| `spring-boot-starter-test` | test | JUnit、Spring Test、Mockito、AssertJ 等测试设施 |
| `spring-security-test` | test | Security 测试辅助工具 |
| `testcontainers-junit-jupiter` + `testcontainers-mysql` | test | 在真实 MySQL 容器上运行集成测试；需要 Docker |

Maven 配置 Java release 17；Spring Boot Maven Plugin 用于运行/打包；Compiler Plugin 保留参数名以支持方法参数映射；Surefire 执行单元测试；`mysql-it` profile 下的 Failsafe 执行集成测试。

### 前端（`frontend/package.json`）

- `vue`：响应式 UI 与组件系统；`vue-router`：页面路由和角色守卫。
- `element-plus`：消息提示与确认/表单对话框等 UI 组件；`axios`：统一 `/api` HTTP 客户端和 JWT 请求头。
- `vite` 与 `@vitejs/plugin-vue`：开发服务器、Vue SFC 转换和生产构建。
- 测试依赖 `vitest`、`@vue/test-utils`、`jsdom`：分别提供测试运行器、Vue 组件挂载与 DOM 环境。

版本锁定在 `frontend/package-lock.json`。项目记录的全量 npm audit 仍有 4 项 moderate，限于开发/测试链 `@vue/test-utils → js-beautify` 的传递依赖；生产依赖审计为 0 项。详情见 [`frontend-dependency-audit.md`](frontend-dependency-audit.md)。

## Java 调用关系与文件职责

Java 源码均在 `backend/src/main/java/com/forgeflow`。通常每条请求按 **Controller → Service → Mapper → MySQL** 流动；Service 注入其他模块 Service 时是同 JVM 的普通 Spring Bean 调用，不经过网络。请求/响应 DTO 与数据库实体分开，Mapper 负责读写。

### 应用启动、认证与权限

- `ForgeFlowApplication.java`：Spring Boot 启动入口，组件扫描 `com.forgeflow`。
- `auth/AuthController.java` 接收 `/api/auth/login`，调用 `AuthService.java`；`AuthService` 通过 `UserMapper.java` 读取 `SysUser.java`，用 `PasswordEncoder` 验证 BCrypt 密码并签发 JWT。
- `auth/LoginRequest.java`、`LoginResponse.java`：登录请求与响应 DTO；`InvalidCredentialsException.java`：统一登录失败异常。
- `auth/SecurityConfig.java`：构造 JWT 编解码器、密码编码器、权限 claim 转换器和无状态 Security 过滤链；计划、审批、仓库、采购、生产、质检岗位的接口边界由服务端强制执行。
- `platform/persistence/MyBatisMapperConfig.java`：扫描 `com.forgeflow` 下的 Mapper 接口。

### 产品、BOM、生产订单与审批

- `erp/product/ProductController.java → ProductService.java → ProductMapper.java`：产品主数据列表/创建；`Product.java` 是产品表实体，`ProductRequest.java` / `ProductView.java` 是输入/输出 DTO。
- BOM 由 `BomLine.java`、`BomLineMapper.java`、`BomLineRequest.java`、`BomLineView.java`、`BomRequest.java` 表示。产品服务通过 `BomLineMapper` 读取或替换 BOM，并用 `ProductMapper` 校验 BOM 引用的原料。
- `erp/order/ProductionOrderController.java → ProductionOrderService.java`：创建、查询生产订单。服务读取 `ProductMapper` 与 `BomLineMapper`，由 `BomRequirementCalculator.java` 按计划数量计算每种原料的四位小数需求；同一事务内写 `ProductionOrder.java` 与 `OrderMaterialRequirement.java` 物料快照，再经 `ProductionOrderMapper.java` / `OrderMaterialRequirementMapper.java` 持久化。`ProductionOrderRequest.java`、`ProductionOrderView.java` 是订单 API DTO。
- OA：`oa/ApprovalController.java → ApprovalService.java → ProductionOrderMapper.java + ApprovalMapper.java`。Service 条件更新生产订单状态，并写入审批动作历史。`ApprovalAction.java` 是历史实体；`ApprovalActionRequest.java`、`ApprovalOrderView.java`、`ApprovalHistoryView.java` 是请求/响应数据结构。

### 库存、采购、MES、质量闭环

- 库存：`inventory/InventoryController.java → InventoryService.java`。服务读取订单快照和产品主数据，调用 `OrderMaterialRequirementMapper.java`、`InventoryBalanceMapper.java`、`InventoryTransactionMapper.java`，完成可用库存查询、原料期初收货、整单预留、领料、采购到货入库、成品入库及流水记录。
- 库存实体为 `InventoryBalance.java`、`InventoryTransaction.java`、`OrderMaterialRequirement.java`；`InventoryDtos.java` 定义余额、需求和收货 DTO。
- 采购：`purchasing/PurchasingController.java → PurchasingService.java`。服务先用 `ProductionOrderMapper` 验证订单已批准，再调用 `InventoryService.requirements()` 求短缺；通过 `PurchaseOrderMapper.java`、`PurchaseOrderLineMapper.java` 写单头/明细。收货时先验证未到数量，再在当前事务中调用 `InventoryService.receivePurchasedMaterial()` 更新库存和流水。`PurchaseOrder.java`、`PurchaseOrderLine.java` 是实体；`PurchaseOrderDtos.java` 是接口 DTO。
- MES：`mes/MesController.java → MesService.java → ProductionOrderMapper.java + ProductionReportMapper.java`。开工前校验订单获批且物料状态为已领料；报工更新订单累计数量、写 `ProductionReport.java`，并校验总报工数不得超过计划。`ProductionReportRequest.java`、`ProductionReportView.java`、`MesWorkOrderView.java` 是 DTO。
- 质量：`quality/QualityController.java → QualityService.java`。从 `ProductionReportMapper` 拉取待检报工，通过 `QualityInspectionMapper.java` 写检验结果；检验通过数量按关联订单产品类型传给 `InventoryService.receiveFinishedGoods()` 入成品库存。`QualityInspection.java` 是实体；`QualityInspectionRequest.java`、`QualityInspectionView.java`、`QualityPendingView.java` 是接口 DTO。

### 总览、平台错误与健康检查

- `dashboard/DashboardController.java → DashboardService.java → DashboardMapper.java → DashboardSummary.java`：由后端查询汇总订单和产出指标。前端 `dashboard/workbench.js` 根据登录岗位只调用其有权访问的现有业务 API，再把草稿、待批、待备/领、短缺待到货、可开工/报工、待检记录整理为真实待办表；`DashboardView.vue` 展示数量与进入业务页的入口，不另造一套绕过业务规则的编辑器。
- `auth/roles.js` 同时定义岗位菜单权限和中文岗位名，`AppShell.vue` 据此绘制导航与登录身份；`route-guard.js` 拒绝未授权前端页面，后端 Spring Security 仍是最终边界。
- `platform/health/HealthController.java`：健康检查 API。
- `platform/error/BusinessRuleException.java`：带错误码与 HTTP 语义的业务异常；`ApiExceptionHandler.java` 将登录失败、业务规则和校验异常转为统一 REST 错误响应。

## 注解说明

### Spring Web 与依赖注入

- `@SpringBootApplication`（`ForgeFlowApplication`）：启动自动配置、组件扫描和应用上下文。
- `@RestController`、`@RequestMapping`、`@GetMapping`、`@PostMapping`、`@PutMapping`：声明 REST 控制器与 URL/HTTP 方法；`@RequestBody` 将 JSON 绑定为 DTO，`@PathVariable` 从 URL 取 ID，`@ResponseStatus` 指定成功响应状态码。
- `@Service`：将业务类注册为可注入 Bean；本项目多用构造器注入。
- `@Configuration`、`@Bean`：在 `SecurityConfig` 和 Mapper 配置类中注册 Security/JWT 配置对象。
- `@Value`：从配置属性注入 JWT secret；`@Autowired`：`AuthService` 的带 `Clock` 测试时钟的构造器注入标记。
- `@RestControllerAdvice`、`@ExceptionHandler`：集中捕获 API 异常并格式化错误响应。

### 事务、ORM 与 MyBatis

- `@Transactional`：界定写事务，使订单及需求快照、预留及流水、采购收货及库存、检验及成品入库等写操作一起提交或回滚；`readOnly = true` 用于只读查询。
- `@TableName`、`@TableId`：把 MyBatis-Plus 实体映射到表名与主键列。数据库字段 snake_case 自动映射到 Java camelCase。
- `@MapperScan`：自动发现 Mapper 接口；`@Mapper` 标记自定义 MyBatis Mapper。
- `@Select`、`@Insert`、`@Update`：在 Mapper 中声明定制 SQL；`@Param` 给 SQL 参数命名，避免依赖编译器参数名推断。

### 请求校验

- `@Valid`：在 Controller 入参上触发 Bean Validation。
- `@NotNull`、`@NotBlank`、`@NotEmpty`：要求值、非空文本或集合存在。
- `@Size`、`@Pattern`：约束长度及编码/格式。
- `@Positive`、`@Min`、`@DecimalMin`：约束数值为正或达到下界。

## 数据库迁移与模拟数据

- `V1`–`V4`：用户身份、ERP 产品/BOM/订单、OA 审批、MES 报工基础表。
- `V5__demo_users.sql` 放在 `db/dev`，只由 `dev` profile 执行，用于创建本地演示账号。
- `V6__inventory_and_material_requirements.sql`：订单物料状态/快照、库存余额及流水。
- `V7__shortage_purchasing.sql`：补料采购单和明细。
- `V8__quality_and_finished_goods.sql`：质检记录与成品入库凭证。
- `V9__demo_manufacturing_data.sql` 放在 `db/dev`，随 `dev` profile 自动准备基础演示产品、BOM 和原料期初库存。
- `V10__job_role_roles.sql`：扩大用户角色约束，允许新增仓库、采购和质检岗位。
- `V11__demo_workbench_data.sql` 放在 `db/dev`：追加多产品、多 BOM、多状态订单、关联审批/库存流水/补料单/MES 报工/质量检验/成品入库数据；演示账号使用 `DEMO_PASSWORD_HASH` placeholder，不写明文口令。`application-dev.properties` 开启 out-of-order 迁移，以支持此前只用 `default` profile 已执行 V1–V8 的本地库补跑 dev 种子。
- 可单独运行的模拟数据文件为 [`database/forgeflow_simulation.sql`](../database/forgeflow_simulation.sql)。它保留快速验证控制器 BOM 短缺 10 块物料的最小样例；岗位队列和多状态演示应使用 `dev` profile 自动执行的 V9/V11，而不是手动运行这份简化 SQL。

## 重要业务约束与演示边界

- 生产订单在创建时冻结 BOM 需求快照，之后 BOM 修改不回写已存在订单。
- 备料前先检查整单所有物料；任一短缺时整单预留失败，不会部分占用库存。领料前要求整单预留完成。
- 采购一张单关联一个生产订单，且仅生成库存短缺部分；到货支持分批，不可超出该明细的未到数量。
- 质量检查只处理 MES“合格报工”数量；质检通过数和拒收数之和必须相等。MES 已报的不合格数量不在质量检验里重复检验。只有通过数量进入成品库存，且每条检验只允许入库一次。
- 这是作品集 MVP；没有供应商、成本/定价、会计、采购审批、库位/批次/条码、返工或多级检验等完整 ERP/MES/QMS 能力。请勿使用于生产业务。
