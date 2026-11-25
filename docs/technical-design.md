# 预约管理系统 — 技术设计文档

> 面向专业服务公司的后端服务。
> REST API + 关系数据库（默认 SQLite；通过 Spring profile 可切换 PostgreSQL / MySQL）。

---

## 1. 概述

本服务让**服务提供商**（律师、顾问、医生、辅导老师等）发布自己可提供服务的**可用时间段（slot）**，
并让**客户**搜索这些时间段、完成预约。系统覆盖**完整预约生命周期**：时段创建 → 可用性搜索 → 预约 →
确认 → 完成 → 取消（自动释放时段）。

设计目标：

| 目标 | 实现方式 |
| --- | --- |
| **正确性** | 关系约束、`@Version` 乐观锁、带条件的状态更新、原子事务 |
| **杜绝重复预订** | 对时段状态做乐观锁 compare-and-set；并发第二个预订者得到 `409 Conflict` |
| **清晰的 API** | RESTful 资源、分页/排序列表、标准 HTTP 动词与状态码 |
| **错误可观测** | 单一全局异常处理器，输出统一、机器可读的错误体 |
| **可移植性** | Spring Data JPA；切换 SQLite → PostgreSQL → MySQL 只需改一个 profile + 一个依赖 |
| **自文档化** | springdoc-openapi（Swagger UI）由控制器自动生成 |

---

## 2. 需求 → 设计映射

| 验收标准 | 设计决策 |
| --- | --- |
| 服务商定义/管理时段（providerId、date、start、end） | `TimeSlot` 实体 + `POST /api/slots`、`GET /api/slots/{id}`、`DELETE /api/slots/{id}` |
| 校验同一服务商时段**不重叠** | Service 层重叠校验 + 守卫条件，并对并发做保护 |
| 时段以初始 **AVAILABLE** 状态持久化 | 枚举 `SlotStatus.AVAILABLE`，创建时赋值 |
| 按 providerId / 日期区间 / 服务类别查询过滤；**分页排序**；排除已预订/锁定/已过期 | `GET /api/slots` 与 `GET /api/slots/available`，用 `Pageable` + `@Specification` 过滤 |
| 预约请求（customerId、slotId / 时间区间），需**健壮性考量** | `POST /api/appointments`：校验 + 可用性检查 + 乐观锁 + 原子预约 |
| 取消时校验客户归属与资格；**原子释放时段**；记录时间戳与原因 | `POST /api/appointments/{id}/cancel` 在 `@Transactional` 单元内完成 |
| 统一全局异常处理：`400`/`404`/`409`，消息清晰 | `@RestControllerAdvice` + 类型化领域异常 + 统一错误体 |

---

## 3. 技术栈与选型理由

| 层 | 选型 | 理由 |
| --- | --- | --- |
| 语言 | Java 17+（字节码目标 21） | 现代 record/密封类友好、LTS |
| 框架 | Spring Boot 3.5.x | 生产级、自动配置、Actuator、生态完善 |
| 持久化 | Spring Data JPA（Hibernate 6） | 实体可移植、分页排序、`@Version`、Specification |
| 数据库 | **SQLite**（默认） | 零配置单文件，开箱即跑、易于验证；`org.xerial:sqlite-jdbc` + Hibernate `community-dialects` |
| 数据库（profile） | PostgreSQL 15+ / MySQL 8+ | `application-postgres.yml` / `application-mysql.yml` |
| 校验 | Jakarta Bean Validation（`spring-boot-starter-validation`） | DTO 声明式约束 |
| API 文档 | springdoc-openapi 2.x | Swagger UI `/swagger-ui.html`、OpenAPI `/v3/api-docs` |
| 构建 | Maven | 环境已安装 |
| 测试 | JUnit 5 + Spring Boot Test + MockMvc | 单元 + 切片 + 集成测试 |

> **本机环境注意：** 当前 `JAVA_HOME` 指向 JDK 8，而 JDK 26 安装在
> `C:\Program Files\Java\jdk-26.0.1`。因此构建时需把 `JAVA_HOME` 指到 JDK 26（见 §14）。
> 项目用 `maven.compiler.release=21` 编译，目标为 Java 21 字节码。

---

## 4. 系统架构

经典分层架构，依赖方向始终向内。

```
┌─────────────────────────────────────────────────────────────────┐
│                     REST/HTTP 客户端（Swagger、curl、Postman）     │
└──────────────────────────────┬──────────────────────────────────┘
                               │ JSON
┌──────────────────────────────▼──────────────────────────────────┐
│  表现层 — controller（providers、customers、slots、appointments）  │
│  · DTO 校验(@Valid)  · DTO ⇄ 领域对象映射                          │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│  服务层 — 事务化业务逻辑                                            │
│  · SlotService、AppointmentService、AvailabilityService          │
│  · 领域规则、状态迁移、并发守卫                                     │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│  仓储层 — Spring Data JPA（PagingAndSortingRepository、             │
│  JpaSpecificationExecutor）                                        │
└──────────────────────────────┬──────────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────────┐
│  数据库 — SQLite（默认）/ PostgreSQL / MySQL                        │
└─────────────────────────────────────────────────────────────────┘
```

横切关注点（集中一处、全局生效）：

- **全局异常处理** — `@RestControllerAdvice`（见 §9）。
- **校验** — DTO 上的 Bean Validation；业务规则在服务层。
- **审计字段** — 通过 JPA Auditing 记录 `createdAt`、`updatedAt`。

---

## 5. 数据模型

### 5.1 实体关系（ER）

```
┌────────────┐ 1      N ┌────────────┐ 1      0..N ┌─────────────────┐ N      1 ┌────────────┐
│  Provider  │──────────│  TimeSlot  │─────────────│   Appointment   │──────────│  Customer  │
└────────────┘          └────────────┘             └─────────────────┘          └────────────┘
  providerId (PK)         slotId (PK)                appointmentId (PK)           customerId (PK)
  name                    providerId (FK)            slotId (FK)                  name
  email (unique)          date                       customerId (FK)              email (unique)
  phone                   startTime                  status                       phone
  serviceCategory         endTime                    bookedAt
                          status                     confirmedAt
                          version                    completedAt
                                                     canceledAt
                                                     cancelReason
                                                     version
```

规则：

- 一个 **Provider** 拥有多个 **TimeSlot**；一个时段只属于一个服务商。
- 一个 **TimeSlot** 可对应**多个** Appointment（`@ManyToOne`）—— 同一时段在其生命周期内可有多个历史预约（例如取消后重新预约）；「同一时刻最多一个进行中的预约」由乐观锁 + 时段状态保证（见 §8）。
- 一个 **Customer** 拥有多个 **Appointment**。

### 5.2 枚举

```java
public enum SlotStatus {
    AVAILABLE,   // 可预订
    LOCKED,      // 临时预留（预订过程中的瞬时态）
    BOOKED,      // 已关联到进行中的预约
    EXPIRED      // 结束时间已过
}

public enum AppointmentStatus {
    BOOKED,      // 已创建，时段被占用
    CONFIRMED,   // 服务商已确认
    COMPLETED,   // 服务已完成
    CANCELED     // 已取消，时段回到 AVAILABLE
}
```

### 5.3 实体字段（摘要）

**Provider（服务商）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `Long` | 主键，自增 |
| `name` | `String` | 非空 |
| `email` | `String` | 唯一、合法邮箱 |
| `phone` | `String` | 可选 |
| `serviceCategory` | `String` | 如 `LEGAL`、`CONSULTING`、`HEALTH`，用于过滤 |
| `createdAt` / `updatedAt` | `LocalDateTime` | 审计 |

**Customer（客户）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `Long` | 主键，自增 |
| `name` | `String` | 非空 |
| `email` | `String` | 唯一、合法邮箱 |
| `phone` | `String` | 可选 |
| `createdAt` / `updatedAt` | `LocalDateTime` | 审计 |

**TimeSlot（可用时段）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `Long` | 主键，自增 |
| `provider` | `Provider` | `@ManyToOne`，必填 |
| `date` | `LocalDate` | 必填 |
| `startTime` | `LocalTime` | 必填，`< endTime` |
| `endTime` | `LocalTime` | 必填 |
| `status` | `SlotStatus` | 默认 `AVAILABLE` |
| `version` | `Long` | `@Version` 乐观锁 |
| `createdAt` / `updatedAt` | `LocalDateTime` | 审计 |

**Appointment（预约）**

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `Long` | 主键，自增 |
| `slot` | `TimeSlot` | `@ManyToOne`，必填 |
| `customer` | `Customer` | `@ManyToOne`，必填 |
| `status` | `AppointmentStatus` | 默认 `BOOKED` |
| `bookedAt` | `LocalDateTime` | 预约时间 |
| `confirmedAt` | `LocalDateTime` | 确认时间 |
| `completedAt` | `LocalDateTime` | 完成时间 |
| `canceledAt` | `LocalDateTime` | 取消时间 |
| `cancelReason` | `String` | 取消时必填 |
| `version` | `Long` | `@Version` 乐观锁 |

---

## 6. 领域生命周期（状态机）

### 6.1 TimeSlot

```
                 create ┌───────────┐   book ┌────────┐
        ┌──────────────▶│ AVAILABLE │───────▶│ BOOKED │
        │               └───────────┘        └────────┘
        │                     │                    ▲
        │                     │ 过期(expire)         │ 取消(cancel，时段释放)
        │                     ▼                    │
        │               ┌───────────┐              │
        │               │  EXPIRED  │              │
        │               └───────────┘              │
        │                                          │
        └──────────────────────────────────────────┘
                     （重新回到 AVAILABLE）
```

- `BOOKED → AVAILABLE` 是唯一“回退”迁移，且**只**发生在把预约置为 `CANCELED` 的同一取消事务内。
- `LOCKED` 是防止重复预订竞态窗口的瞬时预留态；预约流程会立即把它翻转为 `BOOKED` 或回退为 `AVAILABLE`。

### 6.2 Appointment

```
   book         confirm        complete
BOOKED ───────▶ CONFIRMED ────────▶ COMPLETED
   │                │
   └───── cancel ───┴────────────────▶ CANCELED
```

- 只有 `BOOKED` 与 `CONFIRMED` 可取消。
- `CANCELED` 与 `COMPLETED` 为终态。
- 任何非法迁移抛出领域异常（→ `409`）。

---

## 7. REST API 设计

### 7.1 约定

- 基础路径：`/api`
- 输入输出：JSON；`Content-Type: application/json`
- 分页用 Spring `Pageable`：`page`（0 起）、`size`、`sort`（如 `sort=date,asc&sort=startTime,asc`）
- 创建成功 → `201 Created`，响应体返回创建的资源 + `Location` 头
- 更新/状态变更成功 → `200 OK`
- 删除 → `204 No Content`

### 7.2 端点总览

#### Providers（服务商）

| 方法 | 路径 | 说明 | 成功 |
| --- | --- | --- | --- |
| `POST` | `/api/providers` | 创建服务商 | `201` |
| `GET` | `/api/providers` | 分页列出服务商 | `200` |
| `GET` | `/api/providers/{id}` | 获取单个服务商 | `200` |
| `PUT` | `/api/providers/{id}` | 更新服务商 | `200` |
| `DELETE` | `/api/providers/{id}` | 删除服务商（仅无进行中时段时） | `204` |

#### Customers（客户）

| 方法 | 路径 | 说明 | 成功 |
| --- | --- | --- | --- |
| `POST` | `/api/customers` | 创建客户 | `201` |
| `GET` | `/api/customers` | 分页列出客户 | `200` |
| `GET` | `/api/customers/{id}` | 获取单个客户 | `200` |

#### Time Slots（可用时段）

| 方法 | 路径 | 说明 | 成功 |
| --- | --- | --- | --- |
| `POST` | `/api/slots` | 创建可用时段（状态=`AVAILABLE`，校验不重叠） | `201` |
| `GET` | `/api/slots` | 带过滤 + 分页排序查询时段 | `200` |
| `GET` | `/api/slots/available` | **客户搜索**：仅 `AVAILABLE` 且未过期 | `200` |
| `GET` | `/api/slots/{id}` | 获取单一时段 | `200` |
| `DELETE` | `/api/slots/{id}` | 删除时段（仅非 `BOOKED` 时） | `204` |

`GET /api/slots` 与 `GET /api/slots/available` 的过滤参数：

| 参数 | 类型 | 说明 |
| --- | --- | --- |
| `providerId` | `Long` | 按服务商过滤 |
| `startDate` | `LocalDate` | `date` 的含下限 |
| `endDate` | `LocalDate` | `date` 的含上限 |
| `category` | `String` | 按服务商 `serviceCategory` 过滤 |
| `status` | `SlotStatus` | 精确状态过滤（搜索端点固定为 `AVAILABLE`） |
| `page`、`size`、`sort` | — | 分页与排序 |

#### Appointments（预约）

| 方法 | 路径 | 说明 | 成功 |
| --- | --- | --- | --- |
| `POST` | `/api/appointments` | 预约时段（customerId + slotId [+ 可选时间区间]） | `201` |
| `GET` | `/api/appointments` | 列出预约（按客户/服务商/状态过滤） | `200` |
| `GET` | `/api/appointments/{id}` | 获取单个预约 | `200` |
| `POST` | `/api/appointments/{id}/confirm` | 服务商确认 | `200` |
| `POST` | `/api/appointments/{id}/complete` | 标记完成 | `200` |
| `POST` | `/api/appointments/{id}/cancel` | 取消（校验归属 + 资格），释放时段 | `200` |

### 7.3 请求/响应示例

**创建时段**

```http
POST /api/slots
Content-Type: application/json

{
  "providerId": 1,
  "date": "2026-12-01",
  "startTime": "09:00",
  "endTime": "10:00"
}
```

```json
{
  "id": 42,
  "providerId": 1,
  "date": "2026-12-01",
  "startTime": "09:00",
  "endTime": "10:00",
  "status": "AVAILABLE"
}
```

**预约**

```http
POST /api/appointments
Content-Type: application/json

{
  "customerId": 7,
  "slotId": 42
}
```

```json
{
  "id": 100,
  "customerId": 7,
  "slotId": 42,
  "status": "BOOKED",
  "bookedAt": "2026-11-25T19:30:26"
}
```

**取消预约**

```http
POST /api/appointments/100/cancel
Content-Type: application/json

{
  "customerId": 7,
  "reason": "时间冲突"
}
```

```json
{
  "id": 100,
  "customerId": 7,
  "slotId": 42,
  "status": "CANCELED",
  "canceledAt": "2026-11-26T08:15:00",
  "cancelReason": "时间冲突"
}
```

---

## 8. 并发与防重复预订

重复预订是核心健壮性挑战。这里组合两种策略，保证在并发请求下、三种数据库上都成立：

### 8.1 数据模型语义

`Appointment.slot` 为 `@ManyToOne`（不带唯一约束）：同一时段在其生命周期内可对应多个**历史**预约
（例如取消后重新预约）。「同一时段只能有一个**进行中**的预约」由下面 8.2 的乐观锁 + 时段状态共同保证，
而不是靠数据库唯一约束 —— 这样取消释放时段后仍可再次预约，同时并发重复预订仍被拦截。

### 8.2 乐观锁 compare-and-set（SQLite、PostgreSQL、MySQL 均适用）

`TimeSlot` 携带 `@Version` 列。预约流程在单个 `@Transactional` 方法内：

1. 按 id 加载时段（乐观读）。不存在 → `404`。
2. 校验 `status == AVAILABLE`（且未过结束时间）→ 否则 `409 SLOT_ALREADY_BOOKED` / `SLOT_EXPIRED`。
3. 置 `slot.status = BOOKED`（flush 时 JPA 脏检查自动使 `version` 自增）。
4. 创建 `Appointment`，`status = BOOKED`。
5. 提交。若第 1~4 步之间另一事务已预订该时段，Hibernate 抛出
   `OptimisticLockingFailureException`，映射为 `409 CONCURRENT_MODIFICATION`。

由于 flush 时 version 自增是原子 compare-and-set，**恰好一个**并发请求会成功；失败者收到 `409`，
不会产生第二个预约。

### 8.3 事务化取消（原子释放）

取消作为一个事务执行：

1. 加载预约（不存在 → `404`）。
2. 校验 `appointment.customer.id == request.customerId` → 否则 `403 CUSTOMER_NOT_OWNER`。
3. 校验 `status ∈ {BOOKED, CONFIRMED}` → 否则 `409 INVALID_STATE`。
4. 置 `appointment.status = CANCELED`、`canceledAt = now`、`cancelReason = reason`。
5. 置 `appointment.slot.status = AVAILABLE`。
6. 提交。任一步失败则整体回滚 —— 时段绝不会残留在 `BOOKED` 而预约却已 `CANCELED`。

---

## 9. 异常处理与统一错误响应

单一 `@RestControllerAdvice` 把所有异常转换为统一结构：

```json
{
  "timestamp": "2026-11-25T19:30:26.123+08:00",
  "status": 409,
  "error": "Conflict",
  "code": "SLOT_ALREADY_BOOKED",
  "message": "Slot 42 is not available for booking (current status: BOOKED)",
  "path": "/api/appointments",
  "details": []
}
```

| HTTP | `code`（示例） | 触发条件 |
| --- | --- | --- |
| `400` | `VALIDATION_FAILED`、`INVALID_TIME_RANGE` | DTO 校验失败；`startTime >= endTime` |
| `400` | `INVALID_DATE` | 日期/时间无法解析 |
| `404` | `SLOT_NOT_FOUND`、`PROVIDER_NOT_FOUND`、`CUSTOMER_NOT_FOUND`、`APPOINTMENT_NOT_FOUND` | 实体不存在 |
| `409` | `SLOT_OVERLAP` | 服务商已有重叠时段 |
| `409` | `SLOT_ALREADY_BOOKED`、`SLOT_EXPIRED` | 预订了不可用时段 |
| `409` | `CONCURRENT_MODIFICATION` | 乐观锁失败（重复预订竞态） |
| `409` | `INVALID_STATE`、`APPOINTMENT_ALREADY_CANCELED` | 非法状态迁移 |
| `403` | `CUSTOMER_NOT_OWNER` | 非本人取消 |
| `500` | `INTERNAL_ERROR` | 未预期服务器错误（不向客户端泄露堆栈） |

设计要点：

- `ErrorCode` 枚举集中管理错误码；`ApiError` 是序列化 DTO。
- `404`/`400`/`409` 来自类型化领域异常（`ResourceNotFoundException`、`BusinessException`、`ConflictException`），
  使服务层保持可读。
- `500` 绝不向客户端暴露堆栈（只在服务端记录日志）。

---

## 10. 校验规则

**DTO 层（Bean Validation）**

| 规则 | 约束 |
| --- | --- |
| 服务商/客户姓名 | `@NotBlank`、最大长度 |
| 邮箱 | `@Email` |
| 时段 `date` | `@NotNull` |
| 时段 `startTime`/`endTime` | `@NotNull` |
| 预约 `customerId`/`slotId` | `@NotNull`、`@Positive` |
| 取消 `customerId` | `@NotNull`、`@Positive` |
| 取消 `reason` | `@NotBlank`、最大长度 |

**服务层（业务规则）**

| 规则 | 失败 |
| --- | --- |
| `startTime < endTime` | `400 INVALID_TIME_RANGE` |
| 时段日期为今天或未来（拒绝创建过去时段） | `400 INVALID_DATE` |
| 同一服务商 + 同一日期 + 区间重叠 | `409 SLOT_OVERLAP` |
| 预订 `endTime` 已过的时段 | `409 SLOT_EXPIRED` |
| 只允许预订 `AVAILABLE` 时段 | `409 SLOT_ALREADY_BOOKED` |
| 取消需归属 + 资格 | `403` / `409` |

重叠判定（半开区间）：

```
overlap ⇔ 同一 providerId  ∧  同一 date  ∧  a.start < b.end  ∧  a.end > b.start
```

---

## 11. 数据库配置与切换

默认 profile 使用 **SQLite**（无需服务器）。通过激活 profile 并替换一个依赖，即可切换到 PostgreSQL 或 MySQL。

| 数据库 | 激活 profile | 额外依赖 |
| --- | --- | --- |
| SQLite | *(默认)* | `org.xerial:sqlite-jdbc` + `org.hibernate.orm:hibernate-community-dialects` |
| PostgreSQL | `postgres` | `org.postgresql:postgresql` |
| MySQL | `mysql` | `com.mysql:mysql-connector-j` |

`application.yml` 关键配置（SQLite）：

```yaml
spring:
  datasource:
    url: jdbc:sqlite:appointment.db
    driver-class-name: org.sqlite.JDBC
  jpa:
    database-platform: org.hibernate.community.dialect.SQLiteDialect
    hibernate:
      ddl-auto: update
    open-in-view: false
```

- 演示环境用 `ddl-auto: update`；生产应改用 Flyway/Liquibase 迁移（见 §15）。
- `spring.jpa.open-in-view=false` 避免 web 层懒加载反模式。

---

## 12. 项目结构

```
appointment-manage-system-rework/
├── pom.xml
├── README.md
├── docs/
│   └── technical-design.md
├── src/
│   ├── main/java/com/example/appointment/
│   │   ├── AppointmentApplication.java
│   │   ├── config/               # OpenAPI、审计、Jackson 配置
│   │   ├── controller/           # REST 控制器
│   │   ├── dto/                  # 请求/响应 record
│   │   ├── entity/               # JPA 实体 + 枚举
│   │   ├── exception/            # 异常 + 全局处理器 + 错误码
│   │   ├── repository/           # Spring Data 仓储
│   │   ├── service/              # 事务化业务逻辑
│   │   └── spec/                 # 过滤用 JPA Specification
│   ├── main/resources/
│   │   ├── application.yml
│   │   ├── application-postgres.yml
│   │   └── application-mysql.yml
│   └── test/java/com/example/appointment/   # 单元 + 集成测试
```

---

## 13. 测试策略

| 层级 | 范围 | 工具 |
| --- | --- | --- |
| 单元 | 服务层业务规则：重叠判定、状态迁移、校验 | JUnit 5、Mockito |
| 切片 | `@WebMvcTest` + mock 服务；用 `@RestControllerAdvice` 验证错误映射 | MockMvc |
| 集成 | 真实数据库全流程：创建服务商 → 创建时段 → 搜索 → 预订 → 重复预订（期望 `409`）→ 取消（时段回 `AVAILABLE`） | `@SpringBootTest`、SQLite |

关键集成场景：

1. 同一服务商不重叠时段被接受；重叠时段返回 `409 SLOT_OVERLAP`。
2. `GET /api/slots/available` 排除 `BOOKED`、`LOCKED`、已过期（`EXPIRED`）时段。
3. 同一时段的两个并发预订：恰好一个 `201`、一个 `409`。
4. 非本人取消返回 `403`；本人取消原子释放时段。
5. 非法时间区间返回 `400`；时段不存在返回 `404`。

---

## 14. 构建与运行

```bash
# 让 Maven 使用 Java 17+ 的 JDK（本机装有 JDK 26）
export JAVA_HOME="C:\Program Files\Java\jdk-26.0.1"     # bash/WSL
# PowerShell 下：
$env:JAVA_HOME = "C:\Program Files\Java\jdk-26.0.1"

mvn clean package          # 编译 + 跑全部测试
mvn spring-boot:run        # 启动服务（默认：SQLite）
```

- Swagger UI：http://localhost:8080/swagger-ui.html
- OpenAPI JSON：http://localhost:8080/v3/api-docs

改用 PostgreSQL 运行：

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

---

## 15. 未来增强（本次迭代之外）

- Flyway/Liquibase 数据库迁移，面向生产。
- AuthN/AuthZ（JWT），用 provider/customer 角色替代显式 `customerId` 归属校验。
- 时区感知排程（`ZonedDateTime` + 服务商 IANA 时区）。
- 周期性可用性（按周重复）与预约间缓冲时间。
- 邮件/短信通知与提醒。
- 限流、预约幂等键、请求关联 ID。
- OpenTelemetry 指标/追踪与 `/actuator` 健康端点。
