# 预约管理系统（Appointment Management System）

面向专业服务公司的预约管理系统后端服务。服务提供商发布可用时间段，客户搜索可用时间段并完成
**完整预约生命周期**：时段创建 → 可用性搜索 → 预约 → 确认 → 完成 → 取消（自动释放时段）。

## 技术栈

| 层 | 选型 |
| --- | --- |
| 语言 | Java 17+（目标字节码 21） |
| 框架 | Spring Boot 3.5.16 |
| 持久化 | Spring Data JPA（Hibernate 6.6）+ SQLite（默认，可切 PostgreSQL / MySQL） |
| 校验 | Jakarta Bean Validation |
| API 文档 | springdoc-openapi（Swagger UI / OpenAPI） |
| 测试 | JUnit 5 + Mockito + MockMvc |

## 快速开始

### 环境要求

- JDK 17+（本机可用 IntelliJ 自带 JBR 25，路径：`C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.0.1\jbr`）
- Maven 3.10+

> ⚠️ 本机 `JAVA_HOME` 默认指向 JDK 8，构建前必须把它指向 JDK 17+：

```powershell
$env:JAVA_HOME = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.0.1\jbr"
```

### 构建与测试

在**本机受限沙箱环境**下（Maven 写入 `target/` 的文件可能被标记为只读，且 `~/.m2` 不可写），
使用工作区内 `.mvn/settings.xml`（本地仓库重定向到 `.m2-repo/`）并在构建前清除只读属性：

```powershell
$env:JAVA_HOME = "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2.0.1\jbr"
Get-ChildItem target -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object { $_.IsReadOnly = $false }
mvn -s .mvn/settings.xml package
```

在普通开发机上可直接：

```powershell
mvn clean package        # 编译 + 运行全部测试
```

> 测试说明：`src/test/resources/mockito-extensions/` 里配置了 `mock-maker-subclass`，且
> `pom.xml` 为 surefire 设置了 `-Djdk.attach.allowAttachSelf=true`，确保 Mockito 在 JDK 21+
> 测试 JVM 下能稳定初始化。

### 启动

```powershell
mvn spring-boot:run
```

启动后：

- Swagger UI：http://localhost:8080/swagger-ui.html
- OpenAPI JSON：http://localhost:8080/v3/api-docs
- 数据库文件：`appointment.db`（SQLite，自动建表）

## REST API 总览

### Providers（服务商）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/providers` | 创建服务商 |
| `GET` | `/api/providers` | 分页列出 |
| `GET` | `/api/providers/{id}` | 获取单个 |
| `PUT` | `/api/providers/{id}` | 更新 |
| `DELETE` | `/api/providers/{id}` | 删除（仅无时段时） |

### Customers（客户）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/customers` | 创建客户 |
| `GET` | `/api/customers` | 分页列出 |
| `GET` | `/api/customers/{id}` | 获取单个 |

### Time Slots（可用时段）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/slots` | 创建可用时段（校验不重叠） |
| `GET` | `/api/slots` | 过滤 + 分页排序查询 |
| `GET` | `/api/slots/available` | 客户搜索可用时段 |
| `GET` | `/api/slots/{id}` | 获取单个 |
| `DELETE` | `/api/slots/{id}` | 删除（仅非已预订） |

`GET /api/slots` / `GET /api/slots/available` 过滤参数：`providerId`、`startDate`、`endDate`、
`category`、`status`，分页排序用 `page`、`size`、`sort`（如 `sort=date,asc&sort=startTime,asc`）。

### Appointments（预约）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/appointments` | 预约时段 |
| `GET` | `/api/appointments` | 过滤列表（customerId/providerId/status） |
| `GET` | `/api/appointments/{id}` | 获取单个 |
| `POST` | `/api/appointments/{id}/confirm` | 确认 |
| `POST` | `/api/appointments/{id}/complete` | 完成 |
| `POST` | `/api/appointments/{id}/cancel` | 取消（释放时段） |

## 快速示例

```bash
# 1. 创建服务商
curl -X POST http://localhost:8080/api/providers \
  -H "Content-Type: application/json" \
  -d '{"name":"Dr. Li","email":"li@example.com","serviceCategory":"HEALTH"}'

# 2. 创建可用时段
curl -X POST http://localhost:8080/api/slots \
  -H "Content-Type: application/json" \
  -d '{"providerId":1,"date":"2026-12-01","startTime":"09:00","endTime":"10:00"}'

# 3. 搜索可用时段
curl "http://localhost:8080/api/slots/available?category=HEALTH"

# 4. 创建客户并预约
curl -X POST http://localhost:8080/api/customers \
  -H "Content-Type: application/json" \
  -d '{"name":"Alice","email":"alice@example.com"}'

curl -X POST http://localhost:8080/api/appointments \
  -H "Content-Type: application/json" \
  -d '{"customerId":1,"slotId":1}'

# 5. 取消（释放时段）
curl -X POST http://localhost:8080/api/appointments/1/cancel \
  -H "Content-Type: application/json" \
  -d '{"customerId":1,"reason":"时间冲突"}'
```

## 健壮性设计

- **防重复预订**：`Appointment.slot` 唯一约束 + `TimeSlot` 乐观锁（`@Version`）compare-and-set，
  并发第二次预订返回 `409 CONCURRENT_MODIFICATION`。
- **原子取消**：单事务内校验归属（`403`）与资格（`409`）→ 置 `CANCELED` → 释放时段回 `AVAILABLE`。
- **统一异常处理**：`@RestControllerAdvice` 输出统一错误体，`400` / `403` / `404` / `409` / `500`。
- **可用性过滤**：严格排除 `BOOKED` / `LOCKED` / 已过期时段。

## 切换数据库

默认 SQLite。切换到 PostgreSQL / MySQL：加入对应驱动依赖，并以对应 profile 启动。

```bash
# PostgreSQL
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
# MySQL
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

详见 `src/main/resources/application-postgres.yml` 与 `application-mysql.yml`。

## 项目结构

```
src/main/java/com/example/appointment/
├── AppointmentApplication.java
├── config/        # OpenAPI 配置
├── controller/    # REST 控制器
├── dto/           # 请求/响应 record
├── entity/        # JPA 实体 + 枚举
├── exception/     # 异常 + 全局处理器 + 错误码
├── repository/    # Spring Data 仓储
├── service/       # 事务化业务逻辑
└── spec/          # 过滤用 JPA Specification
```

## 文档

- 技术设计文档：[docs/technical-design.md](docs/technical-design.md)
- AI Prompts：[docs/ai-prompts.md](docs/ai-prompts.md)
