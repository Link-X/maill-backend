# 抢票系统后端

![Java](https://img.shields.io/badge/Java-11-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x-brightgreen)
![MySQL](https://img.shields.io/badge/MySQL-8.x-orange)
![Redis](https://img.shields.io/badge/Redis-7.x-red)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.x-ff6600)
![MinIO](https://img.shields.io/badge/MinIO-S3%20compatible-c72e49)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

[English](README.md)

面向千～万级并发场景的抢票系统后端，支持演出管理、选座购票、Redis 库存管理、订单超时自动取消、Mock 支付、入场核验及部分退款。采用 Maven 多模块架构，各模块独立部署。

> **配套前端仓库**：[Link-X/maill-frontend](https://github.com/Link-X/maill-frontend)

---

## 功能特性

- **演出管理**：演出 / 场次 / 座位 CRUD；管理端一键预热座位库存到 Redis
- **分类管理**：独立的 `category` 表 + 管理端 CRUD；演出通过 `categoryId` 关联分类，列表/详情通过 LEFT JOIN 一次返回 `categoryName`，前端首页 tabs 直连 `/api/category/list`
- **城市与地址**：`city` 表 seed 30 个主要城市（GB/T 行政区划代码）；演出含 `cityCode` + `address`，列表/详情/订单/场次详情统一返回 `cityName`；前端首页可按城市切换
- **扩展字段**：`show` / `show_session` 提供 `extend JSON` 字段，产品新增展示型属性时无需 ALTER TABLE，约定写在前端文档（不参与 WHERE/索引）
- **场地模板**：在 Room 上一次性定义座位布局和默认价格；创建场次时传入 `roomId`，座位和价格区域自动复制；提供 `/room/template` 聚合接口一次性返回 room + seats + areas
- **图片上传**：管理端 `/upload/image` 直连 MinIO 对象存储（S3 兼容），支持演出海报、场地图等场景，返回外链 URL
- **统计报表**：管理端 `/api/admin/report/*` 提供 11 个聚合接口（概览/趋势/按演出/分类/城市/状态/时段/场次售罄率/用户/退款/取消率）；时间窗口支持 1d/7d/30d/90d 滚动或自定义；结果 Redis 5 分钟缓存，无需关心 N+1。订单表 `refund_amount` 累计、`cancel_reason` 区分用户/超时取消
- **抢票核心**：Lua 原子限购检查 + Redis 批量锁座（任一失败全量回滚）+ 同步建单
- **防超卖**：Redis Set 原子扣库存，DB 层二次校验兜底
- **订单超时**：RabbitMQ TTL + 死信队列，5 分钟精准触发取消并释放库存
- **异步事件**：支付成功后通过 RabbitMQ Fanout 并行触发票券生成、DB 库存同步、通知（预留）
- **退款**：支持整单退款与单票退款；已部分退款订单（状态 5）可继续退剩余未使用票
- **注解限流**：`@RateLimit` 注解支持全局 / 用户 / IP 三维度固定窗口限流 + 黑名单拦截
- **参数校验**：admin 写接口用专用 `*Request` DTO + `@Valid` 严格约束（前端无法传 id / status / createTime 等不该暴露字段）；全局异常处理统一返回友好错误信息
- **链路追踪**：`TraceIdFilter` 在请求入口生成 traceId，注入 MDC、写入响应头 `X-Trace-Id` 与 Result body `traceId` 字段，便于排障
- **API 文档**：SpringDoc OpenAPI 自动生成 Swagger UI（`/swagger-ui.html`），无需手动维护接口文档
- **入场核验**：支持二维码 / 票号双通道核销
- **JWT 认证**：`@NoLogin` 注解标记免登录接口，其余默认鉴权

---

## 技术栈

| 层次 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 2.7.x / JDK 11 |
| ORM | MyBatis | 3.5.x |
| 缓存 / 分布式锁 | Redis + Redisson | 7.x / 3.x |
| 消息队列 | RabbitMQ | 3.x |
| 数据库 | MySQL | 8.x |
| 对象存储 | MinIO (S3 兼容) | 8.5.x SDK |
| 鉴权 | Spring Security + JJWT | 0.12.x |
| 构建 | Maven | — |

---

## 模块结构

```
maill-backend/
├── common/      # 通用工具：响应封装、异常、雪花ID、RedisKeys、@RateLimit 注解 + AOP、黑名单、TraceIdFilter
├── core/        # 核心业务：实体、Mapper、Service、MQ Producer/Consumer、MyBatis JsonMapTypeHandler
├── admin/       # 管理端 REST API（端口 8081）+ Request DTO + AdminAuthInterceptor
├── user/        # 用户端 REST API（端口 8082）+ LoginCheckInterceptor
├── payment/     # 支付模块（端口 8083，预留）
├── sql/
│   └── schema.sql
└── docker-compose.yml
```

**Service 分层（CQRS-lite）**：单一职责拆分，写命令与查询分离

| Service | 职责 |
|---------|------|
| `ShowService` | Show CRUD |
| `SessionService` | Session CRUD + 发布开售 + 座位图聚合查询 |
| `CategoryService` / `CityService` | 分类 / 城市（CityService 只读） |
| `RoomService` | 场地模板 + copyToSession 复制座位/价格 |
| `OrderCommandService` | 下单 / 取消 / 退款（事务边界） |
| `OrderQueryService` | 单条 / 批量订单查询 + OrderStatusResponse 装配（含批量预取，避免 N+1） |
| `SeatInventoryService` / `PurchaseLimitService` | Redis 库存与限购 |
| `StorageService` | MinIO 上传 |

依赖关系：

```
common ← core ← admin
                 user
                 payment
```

---

## 快速启动

### 前置要求

- JDK 11+
- Maven 3.8+
- Docker & Docker Compose

### 1. 启动基础服务

```bash
docker-compose up -d
```

启动 MySQL 8（3306）、Redis 7（6379）、RabbitMQ 3（5672，管理界面 15672）、MinIO（9000 API / 9001 控制台）。`sql/schema.sql` 首次运行自动执行；MinIO 的 `image` bucket（在 `application-dev.yml` 的 `minio.bucket` 配置）由 admin 启动时自动创建并设为公共读，无需手动建。

> **RabbitMQ 管理界面**：http://localhost:15672（guest / guest）
> **MinIO 管理控制台**：http://localhost:9001（minioadmin / minioadmin123）

### 2. 编译

```bash
mvn compile -q
```

### 3. 启动各模块

```bash
# 管理端（8081）
mvn spring-boot:run -pl admin

# 用户端（8082）
mvn spring-boot:run -pl user
```

默认使用 `dev` profile，数据库密码为 `root123`，可在各模块 `application-dev.yml` 中修改。

服务启动后：
- **Swagger UI**: http://localhost:8081/swagger-ui.html （admin） / http://localhost:8082/swagger-ui.html （user）
- 每个响应自带 `X-Trace-Id` 响应头与 body 内 `traceId` 字段，排障时把这个 ID 给后端即可定位日志

### 4. 生成压测数据（可选）

```bash
# 依赖 jq：brew install jq
bash docs/seed-data.sh
```

自动创建 1 个场地模板（20×20 座位，前 10 行 VIP 区）、5 个演出、15 个场次，并全部发布和预热到 Redis。共 6 000 个可售座位，可直接用于压测。

---

## API 概览

> **详细字段、请求体、返回结构、错误码** 见 Swagger UI（启动服务后访问）：
> - 用户端：http://localhost:8082/swagger-ui.html
> - 管理端：http://localhost:8081/swagger-ui.html
>
> 下表只给"有哪几类接口"的速览，避免文档与代码漂移。

### 用户端（:8082）

| 模块 | 路径前缀 | 主要功能 |
|------|---------|---------|
| 认证 | `/api/auth/*` | 注册 / 登录（返回 JWT） |
| 分类 / 城市（首页 tabs） | `/api/category` `/api/city` | 启用列表，按 sort 排序 |
| 演出 | `/api/show/*` | 列表（name/categoryId/cityCode/venue 筛选）/ 详情，返回 ShowVO（含 categoryName / cityName / address / extend） |
| 场次 | `/api/session/*` | 列表 / 座位图详情（含演出与城市地址） |
| 订单 | `/api/order/*` | 锁座建单 / 取消 / 单票退款 / 我的订单 / 订单详情 |
| 支付 | `/api/payment/*` | 支付订单 |
| 入场核验 | `/api/verify/*` | 二维码 / 票号核销 |

### 管理端（:8081）

| 模块 | 路径前缀 | 主要功能 |
|------|---------|---------|
| 鉴权 | `/api/admin/auth/*` | 管理员注册 / 登录（需 `ADMIN_INVITE_CODE`） |
| 分类管理 | `/api/admin/category/*` | CRUD；删除被引用返回 1012 |
| 城市（只读） | `/api/admin/city/list` | 演出表单下拉源；数据由 schema.sql seed |
| 场地模板 | `/api/admin/room/*` | 场地 CRUD + 座位模板 + 默认价格区域 + `/template` 聚合 |
| 文件上传 | `/api/admin/upload/image` | multipart 上传到 MinIO，返回外链 URL |
| 演出 | `/api/admin/show/*` | Show CRUD（Request DTO 严格校验，status 不接受前端传） |
| 场次 | `/api/admin/session/*` | Session CRUD + `/{id}/publish` 开售；传 roomId 自动复制座位 + 价格 |
| 座位（手动模式） | `/api/admin/seat/*` | 不走场地模板时的座位批量 / 价格区域 / Redis 预热 |
| 订单管理 | `/api/admin/order/*` | 单查 / 列表（showId / sessionId / orderNo / status / 时间筛选） |
| 监控 | `/api/admin/monitor/dashboard` | 场次座位实时统计（总数 / 可售 / 已售） |
| **统计报表** | `/api/admin/report/*` | 11 个接口：概览 / 时间趋势 / 演出/分类/城市排行 / 状态&时段分布 / 场次售罄率 / 用户 / 退款 / 取消率（Redis 5min 缓存） |

---

<!-- 旧的详细 API 表已替换为上方分组速览；具体字段以 Swagger UI 为准 -->

<details>
<summary>历史详细 API 表（已折叠，仅作参考）</summary>

#### 认证

| 方法 | 路径 | 说明 | 登录 |
|------|------|------|:----:|
| POST | `/api/auth/register` | 注册 | ✗ |
| POST | `/api/auth/login` | 登录，返回 JWT | ✗ |

#### 分类 / 城市（首页 tabs）

| 方法 | 路径 | 说明 | 登录 |
|------|------|------|:----:|
| GET  | `/api/category/list` | 启用的分类列表，按 sort 排序 | ✗ |
| GET  | `/api/city/list` | 启用的城市列表，按 sort 排序（30 个主要城市 seed） | ✗ |

#### 演出

| 方法 | 路径 | 说明 | 登录 |
|------|------|------|:----:|
| POST | `/api/show/list` | 演出列表（分页，支持 name / categoryId / cityCode / venue 筛选；item 返回 ShowVO，含 categoryName / cityName / address / extend） | ✗ |
| GET  | `/api/show/{id}` | 演出详情（ShowVO，含 categoryName / cityName / address / extend） | ✗ |

#### 场次

| 方法 | 路径 | 说明 | 登录 |
|------|------|------|:----:|
| POST | `/api/session/list` | 场次列表（分页，支持 status / startTime / endTime 筛选） | ✗ |
| POST | `/api/session/detail` | 场次座位图（含区域价格 + 实时可售状态 + 演出与城市地址信息） | ✗ |

#### 订单

| 方法 | 路径 | 说明 | 登录 |
|------|------|------|:----:|
| POST | `/api/order/submit` | 锁座 + 建单，直接返回完整订单 | ✓ |
| POST | `/api/order/cancel` | 取消订单（未支付直接取消；已支付 / 部分退款则发起退款） | ✓ |
| GET  | `/api/order/orderDetails` | 订单详情（仅限本人；含 showCityName / showAddress 等） | ✓ |
| POST | `/api/order/refundTicket` | 单票退款（支持已支付 / 部分退款订单） | ✓ |
| POST | `/api/order/list` | 我的订单（分页，支持 status / 日期范围筛选） | ✓ |

#### 支付 & 核验

| 方法 | 路径 | 说明 | 登录 |
|------|------|------|:----:|
| POST | `/api/payment/create` | 支付订单 | ✓ |
| POST | `/api/verify/qr` | 二维码核验入场 | ✗ |
| POST | `/api/verify/ticket` | 票号核验入场 | ✗ |

---

### 管理端（:8081）

#### 演出分类管理

独立的 `category` 表，演出通过 `categoryId` 关联；删除被引用的分类会返回 `1012 CATEGORY_IN_USE`。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET    | `/api/admin/category/list?status=&keyword=` | 分类列表，可按 status / keyword 前缀筛选 |
| POST   | `/api/admin/category/create` | 新建分类（name 唯一，重名返回 1013） |
| PUT    | `/api/admin/category/update` | 更新分类（status=0 即"禁用"，用户端列表不返回） |
| DELETE | `/api/admin/category/{id}` | 删除分类（被引用时返回 1012） |

#### 城市（只读）

数据由 `schema.sql` seed，30 个主要城市，不开放 admin 写入。

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/city/list?status=&keyword=` | 后台创建演出时下拉选源 |

#### 场地模板（Room 管理）

在 Room 上一次性定义座位布局和默认价格；创建场次时指定 `roomId`，座位和价格区域自动复制。

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/room/create` | 创建场地 |
| PUT  | `/api/admin/room/update` | 更新场地 |
| GET  | `/api/admin/room/{id}` | 场地详情 |
| GET  | `/api/admin/room/list` | 场地列表 |
| POST | `/api/admin/room/seat/batch` | 保存场地座位模板（覆盖写） |
| GET  | `/api/admin/room/seat/list?roomId=` | 场地座位模板列表 |
| POST | `/api/admin/room/area/save` | 保存场地默认价格区域（覆盖写） |
| GET  | `/api/admin/room/area/list?roomId=` | 场地默认价格区域列表 |
| GET  | `/api/admin/room/template?roomId=` | **聚合查询**：一次返回 room + seats + areas，前端可直接渲染座位图 |

#### 文件上传

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/upload/image` | 上传图片到 MinIO（multipart/form-data；表单字段 `file`；可选 `dir`，默认 `misc`），返回完整可访问 URL |

#### 演出 & 场次

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/show/create` | 创建演出 |
| PUT  | `/api/admin/show/update` | 更新演出 |
| GET  | `/api/admin/show/{id}` | 演出详情 |
| GET  | `/api/admin/show/list` | 演出列表 |
| POST | `/api/admin/session/create` | 创建场次（传入 `roomId` 自动复制座位 + 价格） |
| PUT  | `/api/admin/session/update` | 更新场次 |
| GET  | `/api/admin/session/{id}` | 场次详情 |
| GET  | `/api/admin/session/list?showId=` | 场次列表 |
| PUT  | `/api/admin/session/{sessionId}/publish` | 发布场次开售 |

#### 座位（手动创建——无场地模板时使用）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/admin/seat/batch` | 批量创建座位 |
| GET  | `/api/admin/seat/list?sessionId=` | 座位列表 |
| POST | `/api/admin/seat/area/save` | 保存场次价格区域 |
| GET  | `/api/admin/seat/area/list?sessionId=` | 场次价格区域列表 |
| POST | `/api/admin/seat/warmup/{sessionId}` | 预热座位库存到 Redis |

#### 订单 & 监控

| 方法 | 路径 | 说明 |
|------|------|------|
| GET  | `/api/admin/order/{id}` | 订单详情（原始 Order 实体） |
| GET  | `/api/admin/order/query?orderNo=` | 按订单号查询 |
| GET  | `/api/admin/order/{id}/items` | 订单明细（含座位） |
| POST | `/api/admin/order/list` | **订单分页列表**，支持 showId / sessionId / orderNo / status / 时间范围筛选，返回与用户端同结构（含 show / city / tickets） |
| GET  | `/api/admin/monitor/dashboard?sessionId=` | 实时座位统计（总数 / 可售 / 已售） |

</details>

---

## 核心购票流程

```
用户选座后提交
    │
    ├─ @RateLimit 黑名单 / IP / 用户 / 全局限流（AOP，最先拦截）
    ├─ 场次校验
    ├─ Lua 原子限购检查（Redis）
    ├─ Lua 批量锁座（任一失败全量回滚）
    ├─ 同步建单（DB INSERT）
    ├─ 发送超时消息到 RabbitMQ（TTL = 5 分钟）
    └─ 直接返回完整订单信息（含演出 / 场次 / 座位 / 总价 / 倒计时）
                │
    ┌───────────┴───────────┐
    │                       │
用户在确认页点击支付      5 分钟内未支付
    │                       │
POST /api/payment/create  超时消息经死信路由
    │                  至 order.cancel.queue
    ├─ 创建支付记录         │
    ├─ 订单状态 → 已支付    取消订单
    └─ 发送支付成功事件     释放 Redis 库存
              │             回滚限购计数
    ┌─────────┼──────────┐
    │         │          │
生成票券   同步DB库存   发送通知
（异步）   （异步）    （预留）
```

---

## 退款流程

```
订单状态 1（已支付）或 5（部分退款）
    │
    ├─ 整单取消 POST /api/order/cancel
    │       └─ 查询所有未使用票 → doRefund → 状态 → 退款中(3)
    │
    └─ 单票退款 POST /api/order/refundTicket
            └─ 校验票状态未使用 → doRefund → 状态 → 退款中(3)
                        │
              MQ 消费者处理退款结果
                        │
            ┌───────────┴───────────┐
            │                       │
      仍有未退票             所有票已退
   状态 → 部分退款(5)      状态 → 已退款(4)
```

---

## 订单状态说明

| 状态值 | 含义 |
|:------:|------|
| 0 | 待支付 |
| 1 | 已支付 |
| 2 | 已取消 |
| 3 | 退款中 |
| 4 | 已退款 |
| 5 | 部分退款 |

---

## 消息队列设计

```
订单超时（TTL + 死信）：
  order.timeout.exchange ──→ order.timeout.queue（TTL 5分钟）
                                      │ 到期
  order.dead.exchange    ──→ order.cancel.queue ──→ OrderTimeoutConsumer（取消订单）

支付成功（Fanout）：
  payment.success.exchange ──→ ticket.generate.queue  ──→ 生成票券
                           ──→ inventory.sync.queue   ──→ 同步 seat.status = 已售
                           ──→ notification.queue     ──→ 通知（预留）
```

---

## 注解限流

在任意 Controller 方法上叠加 `@RateLimit` 注解，AOP 自动拦截，无需侵入业务代码：

```java
@RateLimit(type = LimitType.BLACKLIST)
@RateLimit(type = LimitType.IP,     limit = 20,  window = 60)
@RateLimit(type = LimitType.USER,   limit = 5,   window = 60)
@RateLimit(type = LimitType.GLOBAL, limit = 50,  window = 1,  message = "系统繁忙")
@PostMapping("/submit")
public Result<?> submit(...) { }
```

**检查顺序**：黑名单 → IP 限流 → 用户限流 → 全局限流，越早拦截越轻量。

---

## 数据库设计

共 15 张表：

| 表名 | 说明 |
|------|------|
| `user` | 用户，BCrypt 密码 |
| `user_role` | 用户角色（USER / ADMIN） |
| `category` | 演出分类（name 唯一；sort/status 排序与启用控制；含 idx_status_sort 索引） |
| `city` | 城市（GB/T 行政区划代码，code 唯一）；seed 30 个主要城市，不开放写入 |
| `show` | 演出；`category_id` / `city_code` 关联分类与城市；`address` 详细地址；`extend` JSON 扩展字段；含 `idx_name` / `idx_venue` / `idx_category_id` / `idx_city_code` 搜索/筛选索引 |
| `show_session` | 场次；`room_id` 关联场地模板；含限购数 `limit_per_user`；`extend` JSON 扩展字段 |
| `seat` | 座位底表，实时库存由 Redis 管理，支付后异步同步 status |
| `seat_area` | 场次座位价格区域 |
| `order` | 订单；`refund_amount` 累计退款金额、`cancel_reason` 区分用户/超时取消；索引 `idx_status_expire` / `idx_create_time`（报表用） |
| `order_item` | 订单行，含价格快照 |
| `payment` | 支付记录 |
| `ticket` | 票券，8 位友好票号（排除 O/0/I/1）+ UUID 二维码 |
| `room` | 场地模板（名称、行列数等） |
| `room_seat` | 场地座位布局模板 |
| `room_area` | 场地默认价格区域（创建场次时复制到 `seat_area`） |

---

## Redis 设计

| Key | 类型 | 说明 | TTL |
|-----|------|------|-----|
| `session:seats:{sessionId}` | Set | 可售座位 ID 集合 | 7 天 |
| `seat:info:{seatId}` | Hash | 座位详情（行 / 列 / 类型 / 区域） | 7 天 |
| `seat:lock:{sessionId}:{seatId}` | String | 座位锁（value = userId） | 5 分钟 |
| `session:purchase:{sessionId}:{userId}` | String | 用户已购数量 | 7 天 |
| `session:area:price:{sessionId}:{areaId}` | Hash | 区域价格缓存 | 7 天 |
| `session:locked:{sessionId}` | String | 当前正在结算中（已锁座未支付）的座位数量 | 7 天 |
| `rate:global:{method}:{window}` | String | 全局限流计数 | 动态 |
| `rate:user:{userId}:{method}:{window}` | String | 用户限流计数 | 动态 |
| `rate:ip:{ip}:{method}:{window}` | String | IP 限流计数 | 动态 |
| `blacklist:user:{userId}` | String | 用户黑名单 | 自定义 |
| `blacklist:ip:{ip}` | String | IP 黑名单 | 自定义 |

---

## 高并发设计要点

| 问题 | 方案 |
|------|------|
| 超卖 | Redis Set `SREM` 原子扣库存 + DB 层二次校验兜底 |
| 限购 | Lua 脚本原子 INCR + 阈值检查 |
| 流量削峰 | `@RateLimit` 注解限流，全局 / 用户 / IP 三维度 |
| 批量锁座 | Lua 脚本，任一失败全量回滚，不留半锁 |
| 订单超时 | RabbitMQ TTL + 死信队列，精准 5 分钟触发 |
| 支付后解耦 | RabbitMQ Fanout，票券 / 库存 / 通知并行异步处理 |
| 黑名单 | Redis key 存储，AOP 最先检查，不消耗限流计数 |

---

## 安全

- 密码：BCrypt 存储
- 认证：JWT（30 分钟过期），`@NoLogin` 注解标记公开接口
- 越权防护：订单接口校验 `order.userId == 当前登录用户`
- 防注入：MyBatis `#{}` 参数化查询
- 金额校验：后端重新计算总价，不信任前端传值
- 参数校验：`@Valid` + `GlobalExceptionHandler` 统一处理

---

## 部署架构

```
                      ┌──────────────────┐
                      │      Nginx       │
                      │  (反向代理/SSL)   │
                      └────────┬─────────┘
                               │
                  ┌────────────┴────────────┐
                  │                         │
         ┌────────▼────────┐      ┌────────▼────────┐
         │  admin : 8081   │      │  user  : 8082   │
         │    管理端 API    │      │    用户端 API    │
         └────────┬────────┘      └────────┬────────┘
                  │                         │
                  └────────────┬────────────┘
                               │
            ┌──────────────────┼──────────────────┐
            │                  │                  │
   ┌────────▼────────┐ ┌───────▼──────┐ ┌────────▼────────┐
   │    MySQL 8      │ │   Redis 7    │ │   RabbitMQ 3    │
   │   (主从可选)     │ │  (缓存/锁)   │ │  (事件 / 超时)  │
   └─────────────────┘ └──────────────┘ └─────────────────┘
```

---

## 扩展方向

- **真实支付**：实现 `PaymentGateway` 接口对接支付宝 / 微信
- **通知服务**：接入短信 / 推送，实现 `notification.queue` 消费者
- **微服务化**：admin / user / payment 拆分独立部署 + API Gateway
- **分库分表**：订单表按 `session_id` 分片
- **CDN**：演出海报等静态资源加速；上线时把 MinIO 切换为阿里云 OSS / AWS S3，只改 `minio.endpoint` / `accessKey` 即可

---

## License

[MIT](LICENSE)
