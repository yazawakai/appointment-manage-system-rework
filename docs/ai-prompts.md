# AI Prompts（构建过程使用的提示词记录）

> 本文件记录从需求到实现过程中使用的核心 AI 提示词，供“10 分钟实现简报”复现与说明。

## 1. 需求理解与技术选型

```text
使用 REST API 和关系数据库（SQLite、PostgreSQL 或 MySQL）为专业服务公司开发一个强大的
预约管理系统后端服务。该服务使服务提供商能够管理其可用时间段，并允许客户搜索可用时间段。
请使用完整的预约生命周期管理和足够强大的系统来实施该系统。先生成一个技术文档。
```

**补充确认提示词（交互式）**

```text
默认使用哪个关系数据库？—— 选择 SQLite（默认）。
技术文档主体用哪种语言？—— 选择中文。
```

## 2. 技术设计文档

```text
编写一份技术设计文档，包含：概述与目标、需求到设计的映射、技术栈与选型理由、
系统分层架构、数据模型（实体关系图、字段、枚举）、领域状态机（时段与预约生命周期）、
REST API 设计（约定、端点、请求/响应示例）、并发与防重复预订策略、
统一异常处理与标准错误体、校验规则、数据库配置与切换、项目结构、测试策略、
构建与运行说明、未来增强。
```

## 3. 代码实现（分模块）

```text
用 Spring Boot 3.5 + Spring Data JPA + SQLite 实现：
1) 实体 Provider / Customer / TimeSlot / Appointment 及状态枚举，TimeSlot 与 Appointment 带 @Version 乐观锁；
2) Repository 支持分页排序与 JpaSpecificationExecutor；
3) DTO（record）与 Bean Validation 校验；
4) Service：时段创建（校验时间区间、非过去、同服务商不重叠）、可用性搜索（排除已预订/锁定/过期）、
   预约（校验客户/时段存在、状态 AVAILABLE、未过期，乐观锁防并发）、
   取消（校验归属 403、资格 409，事务内原子释放时段回 AVAILABLE）；
5) Controller 与统一全局异常处理（400/403/404/409/500 标准错误体）；
6) springdoc-openapi 集成。
```

## 4. 测试

```text
编写单元测试（非法时间区间、起止相等、过去日期 → BadRequestException）与集成测试：
创建服务商 → 创建时段 → 重叠时段返回 409 → 可用性搜索排除已预订/已过期 →
顺序重复预订返回 409 → 并发预订恰好一个成功 → 非本人取消 403 → 本人取消释放时段 →
重复取消 409 → 不存在资源 404。
```

## 5. 交付物整理

```text
生成 README（技术栈、构建运行、API 总览、示例、健壮性说明）与 AI Prompts 记录文件，
并在最终回复中给出 10 分钟实现简报。
```

---

## 关键实现决策（供简报说明）

1. **数据库默认 SQLite**：零配置、单文件，开箱即跑；通过 Spring profile + 驱动依赖切换 PostgreSQL/MySQL。
2. **防重复预订**：`Appointment.slot` 唯一约束（结构层）+ `TimeSlot.@Version` 乐观锁（并发层），
   并发第二个请求在 flush 时版本号不匹配 → `409 CONCURRENT_MODIFICATION`。
3. **原子取消**：单 `@Transactional` 方法内「校验归属/资格 → 置 CANCELED → 释放时段」，
   任一步失败整体回滚。
4. **统一错误体**：`ApiError{timestamp,status,error,code,message,path,details}`，
   由 `@RestControllerAdvice` 集中生成。
5. **可用性语义**：`/api/slots/available` 用 `Specification` 组合过滤，严格排除
   `BOOKED`/`LOCKED` 以及「结束时间已过」的时段。
