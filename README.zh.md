# 抢票系统后端

![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.x-brightgreen)
![MySQL](https://img.shields.io/badge/MySQL-8.x-orange)
![Redis](https://img.shields.io/badge/Redis-7.x-red)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.x-ff6600)
![MinIO](https://img.shields.io/badge/MinIO-S3%20compatible-c72e49)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

[English](README.md)

面向千级持续 QPS + 万级抢票峰值场景设计的抢票系统后端,架构层面覆盖了核心模式(Redis Lua 原子锁座、本地缓存 + pub/sub 失效、异步建单、MQ 驱动订单超时、分布式调度、状态机乐观锁等),真正达到生产万级仍需横向扩展 + 库存分片。功能含演出管理、**混合售卖模式(选座 + 系统派座)**、Redis 库存、订单超时、Mock 支付、入场核验、部分退款、全文搜索、站内消息、评价、监控等。Maven 多模块架构,独立部署。

> **配套前端仓库**：[Link-X/maill-frontend](https://github.com/Link-X/maill-frontend)

---

## 功能特性

- **演出管理**：演出 / 场次 / 座位 CRUD；管理端一键预热座位库存到 Redis
- **分类管理**：独立的 `category` 表 + 管理端 CRUD；演出通过 `categoryId` 关联分类，列表/详情通过 LEFT JOIN 一次返回 `categoryName`，前端首页 tabs 直连 `/api/category/list`
- **城市与地址**：`city` 表 seed 30 个主要城市（GB/T 行政区划代码）；演出含 `cityCode` + `address`，列表/详情/订单/场次详情统一返回 `cityName`；前端首页可按城市切换
- **扩展字段**：`show` / `show_session` 提供 `extend JSON` 字段，产品新增展示型属性时无需 ALTER TABLE，约定写在前端文档（不参与 WHERE/索引）
- **场地模板**：在 Room 上一次性定义座位布局和默认价格；创建场次时传入 `roomId`，座位和价格区域自动复制；提供 `/room/template` 聚合接口一次性返回 room + seats + areas
- **图片上传**：管理端 `/upload/image` 直连 MinIO 对象存储（S3 兼容），支持演出海报、场地图等场景，返回外链 URL
- **艺人系统**：`artist` 表 + `show_artist` 多对多关联；用户可关注/取关艺人（`user_follow_artist`），演出详情自动注入艺人列表（join `show_artist`），用户端按艺人筛选资讯
- **资讯中心**：`article_category` + `article` 双表；草稿/已发布/已下架三态；支持封面图、富文本、关联艺人，用户端列表按分类+发布时间倒序
- **首页 Banner**：`banner` 表支持图片+跳转目标（演出/艺人/资讯/外链）；定时上下架（`start_at`/`end_at`）+ 排序；用户端 `/api/banner/list` 一次取回有效轮播
- **收藏分组**：`favorite_group` + `user_favorite`；用户可自定义分组（默认未分组），收藏唯一约束 `(user_id, show_id)`，支持跨分组移动
- **开售提醒**:订阅维度=演出,推送维度=场次。`SubscribeNotifier` 每分钟扫描,按 `(订阅, 开售日)` 分组,同演出同日多场合并成一条消息(如"今日 3 场即将开售:18:00 / 20:30 / 22:00"),避免巡演场景下消息洪水。按场次幂等存于 `show_subscribe_session_notify`
- **站内消息**：`message` + `user_message` 两表分离；支持单发/广播（订单/开售/系统/互动/关注动态五类），用户端列表/未读数/标记已读/批量删除
- **演出评价**：一级评论 + 二级回复（`parent_id` 自关联）；评分仅一级且 1-5 星；图片晒图、点赞（去重 `uk_review_user`）、举报、管理端审核（隐藏/恢复/删除）；演出可配置 `review_mode`（无评价/所有可评/仅已观看）+ `avg_rating`/`review_count` 冗余统计
- **统计报表**：管理端 `/api/admin/report/*` 提供 11 个聚合接口（概览/趋势/按演出/分类/城市/状态/时段/场次售罄率/用户/退款/取消率）；时间窗口支持 1d/7d/30d/90d 滚动或自定义；结果 Redis 5 分钟缓存，无需关心 N+1。订单表 `refund_amount` 累计、`cancel_reason` 区分用户/超时取消
- **全文搜索**：Elasticsearch 8.x 异步索引演出/艺人/资讯三类文档；业务写操作发布 `search.sync.queue` 事件，`SearchSyncConsumer` 落 ES，`IndexInitializer` 启动时幂等建索引；ES 不可用时降级，不阻塞业务
- **抢票核心(异步建单)**:`/api/order/submit/by-seats` 同步只做"校验 + 限购 + Lua 批量锁座 + 预生成 orderNo + 发 MQ",立即返回(约 5ms)。真正 `INSERT order` 由 `OrderCreateConsumer` 异步消费,前端用 orderNo 轮询 `/api/order/createStatus` 直到 SUCCESS/FAILED。锁座与 DB 写入解耦,单实例 QPS 上限从几百提升到几千
- **混合售卖(选座/派座并存)**:`seat_area.sale_mode` 区域级配置 — 同一场次的不同区域可独立设为「用户选座」或「系统派座」(VIP 区让用户挑,看台区系统派,典型大型演唱会模式)。派座走 `/api/order/submit/by-area`,接受「区域 + 票种 + 数量」,后端同步原子扣库存 + 异步从池中 `ZPOPMIN` 派座 + 建单
- **情侣座保护(5 层防御)**:派座 warmup 时按 `seat.type` 把单座与情侣对**物理隔离**为两个池 (`area:pool:single` / `area:pool:couple`),`ticketType=1` 取单座池、`ticketType=2` 取情侣对池,情侣对池 member 形如 `"leftId:rightId"`,ZPOPMIN 操作天然原子,**绝不可能把情侣对的一个派给单座用户**。配合录入成对约束、选座完整性校验、退款成对约束,共 5 层防御
- **派座任务持久化 + 自愈**:派座流程「同步扣库存 → 异步派座 → 建单 → task=SUCCESS」涉及多个步骤跨 Redis + MySQL,中间状态全部落 `seat_allocation_task`,`task=SUCCESS` 与 `INSERT order` 在同一事务内原子提交;`SeatAllocationRecoveryScheduler` 每分钟扫超时 PENDING 任务,按 `allocated_seats` 是否为空区分回滚方式(仅补库存 / 补库存+还池),消费者崩溃也能恢复
- **订单超时兜底扫描**:`OrderTimeoutScanScheduler` 每分钟扫 `expire_time < now-60s` 且 `status=0` 的订单,与 MQ 延迟队列双轨触发取消,即使 MQ 失败也能保证座位最终释放
- **防超卖**:Redis Set 原子扣库存,DB 层二次校验兜底
- **订单超时**:RabbitMQ TTL + 死信队列,5 分钟精准触发取消并释放库存
- **异步事件**:支付成功后通过 RabbitMQ Fanout 并行触发票券生成、DB 库存同步、通知(预留)
- **退款**:支持整单退款与单票退款;已部分退款订单(状态 5)可继续退剩余未使用票
- **场次状态机自动流转**:`SessionLifecycleScheduler` 到 `openSaleTime` 自动 `0→1`(自动 warmup Redis 库存 + 开售),到 `endTime` 自动 `0/1→2`(结束)。管理员只配数据,无需手动发布/预热,流转用乐观锁保证幂等
- **本地缓存 + pub/sub 失效**:座位结构 / 价格 / 演出 / 城市走 Caffeine,防止热门场次刷新打爆 DB。写操作通过 Redis pub/sub 广播,毫秒级使各实例 Caffeine 失效(多实例一致性)。场次信息(含可变 status)直查 DB 不入缓存,避免多实例下读到旧状态
- **分布式调度(ShedLock)**:所有 `@Scheduled` 任务通过 Redis 锁互斥,多实例部署下同一时刻只有一个实例真正执行;持锁实例挂了下一分钟其他实例自动接管(故障转移)
- **可观测性(Prometheus + Grafana)**:所有应用暴露 `/actuator/prometheus`,自带 docker-compose 起 Prometheus + Grafana,预置概览看板(JVM 堆、HTTP QPS、p95/p99、HikariCP 连接池、5xx 错误率、RabbitMQ 消息速率)
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
| 后端框架 | Spring Boot | 3.2.x / JDK 17 |
| ORM | MyBatis | 3.5.x |
| 缓存 / 分布式锁 | Redis + Redisson | 7.x / 3.27.x |
| 本地缓存 | Caffeine | (Spring Boot 管理) |
| 分布式调度 | ShedLock | 5.13.x |
| 消息队列 | RabbitMQ | 3.x |
| 数据库 | MySQL | 8.x |
| 全文搜索 | Elasticsearch (Java API Client) | 8.13.x |
| 对象存储 | MinIO (S3 兼容) | 8.5.x SDK |
| 鉴权 | Spring Security + JJWT | 0.12.x |
| 监控 | Micrometer + Prometheus + Grafana | 1.x / 2.51 / 10.4 |
| 构建 | Maven | — |

---

## 模块结构

```
maill-backend/
├── common/      # 通用工具：响应封装、异常、雪花ID、RedisKeys、@RateLimit AOP、黑名单、TraceIdFilter
│   └── es/      # Elasticsearch 客户端配置 + 索引 mapping + IndexInitializer（启动幂等建索引）
├── core/        # 核心业务：实体、Mapper、Service、MQ Producer/Consumer、JsonMapTypeHandler
│   ├── cache/     # CacheInvalidationBroadcaster/Listener：Redis pub/sub 广播 Caffeine 失效(多实例一致性)
│   ├── config/    # ShedLockConfig(Redis 分布式锁)、CacheInvalidationConfig(pub/sub 订阅)等
│   └── scheduler/ # 定时任务(全部 @SchedulerLock 互斥)：
│                  #   SubscribeNotifier(开售提醒,按场次按日合并)
│                  #   SessionLifecycleScheduler(场次状态自动流转 0→1 + 0/1→2)
│                  #   ArticleViewFlushScheduler(资讯浏览数 Redis → DB 回写)
│                  #   SeatAllocationRecoveryScheduler(派座超时任务回滚 + 库存/池修复)
│                  #   OrderTimeoutScanScheduler(订单超时兜底取消,MQ 失败也能释放座位)
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
| `ShowService` | Show CRUD + 关联艺人维护 + 发布 ES 同步事件 |
| `SessionService` | Session CRUD + 发布开售 + 座位图聚合查询 |
| `CategoryService` / `CityService` | 分类 / 城市（CityService 只读） |
| `RoomService` | 场地模板 + copyToSession 复制座位/价格 |
| `OrderCommandService` | 下单 / 取消 / 退款(事务边界);拆三层 — 选座入口 / 派座入口 / 纯建单核心(`createOrderInternal` 走 `@Transactional` self 代理) |
| `OrderQueryService` | 单条 / 批量订单查询 + OrderStatusResponse 装配（含批量预取，避免 N+1） |
| `SeatInventoryService` / `PurchaseLimitService` | Redis 库存与限购(选座的 SET+lock + 派座的 stock 计数器与 single/couple 池) |
| `SeatAllocationService` | 派座两段式原语:`reserveStock`(原子扣库存) + `allocate`(ZPOPMIN 取池);情侣对池天然成对 |
| `ArtistService` / `ArticleService` / `ArticleCategoryService` / `BannerService` | 艺人 / 资讯 / 资讯分类 / Banner（带 ES 同步） |
| `FavoriteService` / `SubscribeService` | 收藏分组 + 订阅开售提醒 |
| `MessageService` | 站内信单发 / 广播 / 已读 / 删除 |
| `ReviewService` | 评价发布、回复、点赞、举报、评分聚合（DB 维护 `avg_rating` / `review_count`） |
| `SearchService` | ES 多索引查询 + 搜索历史 |
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

- JDK 17+
- Maven 3.8+
- Docker & Docker Compose

### 1. 启动基础服务

```bash
docker-compose up -d
```

启动 MySQL 8(3306)、Redis 7(6379)、RabbitMQ 3(5672,管理界面 15672)、MinIO(9000 API / 9001 控制台)、Elasticsearch 8.13(9200)、Prometheus(9090)、Grafana(3000)。`sql/schema.sql` 首次运行自动执行;MinIO 的 `image` bucket 由配套的一次性 init 容器 `ticket-minio-init` 在 MinIO 健康后自动建好并设为匿名可读(基础设施层就完成,不依赖应用启动顺序);ES 三个索引(show / artist / article)由 `IndexInitializer` 启动时幂等创建;Grafana 首次启动自动注入 Prometheus 数据源 + 预置 "Ticket 系统概览" dashboard。均无需手动建。

> **MinIO bucket 兜底**:即使没用 docker-compose(例如手动起的 MinIO),admin 应用在 `ApplicationReadyEvent` 时也会重试 5 次去 `ensureBucket + setPublicReadPolicy`,失败会记 ERROR 日志便于告警。

> **RabbitMQ 管理界面**:http://localhost:15672(guest / guest)
> **MinIO 管理控制台**:http://localhost:9001(minioadmin / minioadmin123)
> **Elasticsearch**:http://localhost:9200(无鉴权,单节点 dev 模式)
> **Prometheus**:http://localhost:9090(抓取各 Spring Boot 应用 `/actuator/prometheus`)
> **Grafana**:http://localhost:3000(admin / admin,预置"Ticket 系统概览"看板)

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

## 核心购票流程(异步建单)

### 选座模式 — POST /api/order/submit/by-seats

```
用户选座后提交 — POST /api/order/submit/by-seats
    │
    ├─ @RateLimit 黑名单 / IP / 用户 / 全局限流(AOP,最先拦截)
    ├─ 场次校验(status / openSaleTime / endTime)
    ├─ 限购校验 + 扣减(Redis)
    ├─ Lua 批量锁座(任一失败全量回滚)
    ├─ 预生成 orderNo(雪花 ID)
    ├─ 写 Redis pending key:order:create:pending:{orderNo} = "PROCESSING" (TTL 60s)
    ├─ 发 OrderCreateMessage 到 order.create.queue
    └─ 立即返回 { orderNo, status: "PROCESSING" }   (约 5ms)
                │
                ├──────────────── (异步) ───────────────┐
                │                                       │
   前端用 orderNo 轮询                       OrderCreateConsumer
   /api/order/createStatus 直到 SUCCESS/FAILED         消费消息
                │                                       │
                │                         ├─ 幂等:selectByOrderNo,
                │                         │   存在直接返回
                │                         ├─ 超卖兜底(DB)
                │                         ├─ 加载座位 / 校验情侣座
                │                         ├─ 计算总价(走 Redis)
                │                         ├─ INSERT order + INSERT order_item(同事务)
                │                         └─ afterCommit:
                │                              consumeSeat() 消费座位
                │                              sendTimeoutMessage() 发超时 MQ
                │                                       │
   ┌────────────┴────────────┐                          │
   │                         │                          │
SUCCESS                    FAILED                       │
DB 命中 orderNo            Redis pending=FAILED:reason  │
→ 返回完整订单详情         → 前端展示失败原因           │
   │
用户点支付 → POST /api/payment/create
   │
   ├─ 创建支付记录
   ├─ 订单状态 → 已支付
   └─ 发送支付成功事件(Fanout)
              │
    ┌─────────┼──────────┐
    │         │          │
 生成票券  同步 DB    发送通知
 (异步)    库存        (预留)

   --- 用户 5 分钟未支付 ---
   超时消息经死信路由 → order.cancel.queue → 取消订单 + 释放库存
```

> **为什么异步**:`submit` 同步建单,平均 50-100ms,DB 写入是瓶颈。改成只同步锁座,建单丢给消费者,`submit` 返回降到 5ms。用户体感"占座成功"立即响应,单实例 QPS 上限从 ~500 提升到 ~2000+。
>
> **失败处理**:消费者遇业务异常(超卖兜底命中、价格丢失)— `OrderCommandService` 内部已经释放座位+退限购,消费者把 pending key 标 FAILED 后**不抛出**(避免 MQ 重试)。前端下次轮询读到 FAILED 显示原因。遇系统异常(DB 短暂不可用)— 抛出让 MQ 自动重试 3 次,用户期间看到 PROCESSING,最终 SUCCESS 或 FAILED。

### 派座模式 — POST /api/order/submit/by-area

```
用户选区域+票种+数量 — POST /api/order/submit/by-area  { sessionId, areaId, ticketType, quantity }
    │
    ├─ @RateLimit 黑名单 / IP / 用户 / 全局限流
    ├─ 场次校验
    ├─ 限购校验 + 扣减(Redis;情侣对按 *2 算张数)
    ├─ allocationService.reserveStock — 原子 DECR area:stock:{single|couple}(Lua)
    │      └─ 校验区域 saleMode=2 + 票种存在;库存不足立即抛 STOCK_NOT_ENOUGH
    ├─ 预生成 orderNo
    ├─ INSERT seat_allocation_task = PENDING        ← 中间状态落库,scheduler 据此回滚
    ├─ 写 Redis pending key
    ├─ 发 OrderAllocateMessage 到 order.allocate.queue
    └─ 立即返回 { orderNo, status: "PROCESSING" }
                │
                ├──────────────── (异步) ──────────────┐
                │                                       │
   前端用 orderNo 轮询                       OrderAllocateConsumer
   /api/order/createStatus                            消费消息
                │                                       │
                │                         ├─ 幂等:DB 已有订单 → CAS task=SUCCESS + 删 pending + return
                │                         ├─ task=SUCCESS/ROLLED_BACK → 短路返回或抛 BusinessException
                │                         ├─ allocationService.allocate — Lua ZPOPMIN 池中前 N 个
                │                         │       (single 池:member=seatId;couple 池:member="left:right")
                │                         ├─ task.allocated_seats 立即落库(供 scheduler 区分回滚方式)
                │                         ├─ self.createOrderInternal(@Transactional):
                │                         │       INSERT order + items + 同事务内 task=SUCCESS
                │                         └─ afterCommit:
                │                              consumeAllocatedSeat() — SREM session:seats
                │                              sendTimeoutMessage() — 发超时 MQ
                │
   ┌────────────┴────────────┐
   │                         │
SUCCESS                    FAILED
DB 命中 orderNo            Redis pending=FAILED:reason
→ 返回完整订单详情         → 前端展示失败原因
   │
   └─ 之后流程同选座模式(支付 → 票券 / 库存同步 / 通知)
```

> **为什么两段式扣库存**:抢购热路径只动 `area:stock` 计数器(原子 DECR),不去碰池子 ZPOPMIN,失败立即响应,极低开销;真正取座异步做。
>
> **情侣保护核心**:warmup 时按 `seat.type` 把单座(`type=1`)和情侣对(`type=2/3` 成对)**物理分到两个池**。单座池里**永远不会出现情侣座**,派单座的 ZPOPMIN 根本看不到情侣座 → 不可能派错。情侣对池的每个 member 就是"一对",ZPOPMIN 单 member 取出 = 一对座位原子取出,绝不可能拆散。
>
> **崩溃恢复**:派座中间状态由 `seat_allocation_task` 落库;`SeatAllocationRecoveryScheduler` 每分钟扫超过 2min 仍 PENDING 的任务,据 `allocated_seats` 是否为空选择"仅回滚库存"或"回滚库存+还池";消费者 INSERT 后崩溃也安全(`task=SUCCESS` 与 INSERT 同事务原子提交)。

---

## 退款流程

```
订单状态 1（已支付）或 5（部分退款）
    │
    ├─ 整单取消 POST /api/order/cancel
    │       └─ 查询所有未使用票 → doRefund → 状态 → 退款中(3)
    │
    └─ 单票退款 POST /api/order/refundTicket
            ├─ 情侣座单票退款拦截(必须整单退,保证成对) → 抛业务异常
            └─ 校验票状态未使用 → doRefund → 状态 → 退款中(3)
                        │
              MQ 消费者处理退款结果
                        │
            ┌───────────┴───────────┐
            │                       │
      仍有未退票             所有票已退
   状态 → 部分退款(5)      状态 → 已退款(4)
```

> **释放分流**:`releaseSeatsByMode` 按每个 seat 所属区域的 `sale_mode` 自动选择释放路径 — 选座区:回 `session:seats` SET + 删 `seat:lock` + DECR 锁定计数;派座区:在此基础上额外把座位还回对应 `area:pool` + INCR `area:stock`。情侣对成对回 couple 池,绝不拆散。

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
异步建单 — 选座模式(Direct):
  order.create.exchange ──→ order.create.queue ──→ OrderCreateConsumer(INSERT order + items + 发超时 MQ)
                              (内置 3 次重试;最终失败标记 Redis pending key 为 FAILED)

异步建单 — 派座模式(Direct):
  order.allocate.exchange ──→ order.allocate.queue ──→ OrderAllocateConsumer
                              (ZPOPMIN 取池 + INSERT order + task=SUCCESS 同事务;失败回滚库存+池)

订单超时（TTL + 死信）：
  order.timeout.exchange ──→ order.timeout.queue（TTL 5分钟）
                                      │ 到期
  order.dead.exchange    ──→ order.cancel.queue ──→ OrderTimeoutConsumer（取消订单）

支付成功（Fanout）：
  payment.success.exchange ──→ ticket.generate.queue  ──→ 生成票券
                           ──→ inventory.sync.queue   ──→ 同步 seat.status = 已售
                           ──→ notification.queue     ──→ 通知（预留）

退款（Direct）：
  refund.exchange ──→ refund.queue ──→ RefundConsumer（按未退票数计算订单终态）

搜索同步（Direct，演出/艺人/资讯写操作后异步落 ES）：
  search.sync.exchange ──→ search.sync.queue ──→ SearchSyncConsumer（upsert/delete ES 文档）
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

共 30 张表，按业务域分组：

**核心交易（演出 / 场次 / 座位 / 订单 / 支付）**

| 表名 | 说明 |
|------|------|
| `user` | 用户，BCrypt 密码 |
| `user_role` | 用户角色（USER / ADMIN） |
| `category` | 演出分类（name 唯一；sort/status 排序与启用控制；含 idx_status_sort 索引） |
| `city` | 城市（GB/T 行政区划代码，code 唯一）；seed 30 个主要城市，不开放写入 |
| `show` | 演出；`category_id` / `city_code` 关联分类与城市；`address` 详细地址；`extend` JSON；`review_mode` 评价模式 + `avg_rating` / `review_count` 评分冗余；`open_sale_time` 开售时间；含 `idx_name` / `idx_venue` / `idx_category_id` / `idx_city_code` / `idx_open_sale_time` 索引 |
| `show_session` | 场次；`room_id` 关联场地模板；含限购数 `limit_per_user`；`extend` JSON 扩展字段 |
| `seat` | 座位底表(`type` 1=普通/2=情侣左/3=情侣右,`pair_seat_id` 互指),实时库存由 Redis 管理,支付后异步同步 status |
| `seat_area` | 场次座位价格区域;`sale_mode`(1=选座/2=派座)、`single_total` / `couple_total`(派座统计)、`allocate_strategy`(派座策略) |
| `seat_allocation_task` | 派座任务表(中间状态持久化);字段:`order_no` UNIQUE / `ticket_type` / `quantity` / `status`(0待派 1成功 2失败 3已回滚)/ `allocated_seats` CSV;`idx_status_create` 用于 scheduler 超时扫描 |
| `order` | 订单；`refund_amount` 累计退款金额、`cancel_reason` 区分用户/超时取消；索引 `idx_status_expire` / `idx_create_time`（报表用） |
| `order_item` | 订单行，含价格快照 |
| `payment` | 支付记录 |
| `ticket` | 票券，8 位友好票号（排除 O/0/I/1）+ UUID 二维码 |
| `room` | 场地模板（名称、行列数等） |
| `room_seat` | 场地座位布局模板 |
| `room_area` | 场地默认价格区域（创建场次时复制到 `seat_area`） |

**运营内容（艺人 / 资讯 / Banner）**

| 表名 | 说明 |
|------|------|
| `artist` | 艺人主表（本名 / 艺名 / 头像 / 标签 / `social_links` JSON / `follow_count` / `show_count` 冗余） |
| `show_artist` | 演出-艺人多对多关联（`role` 主演/导演/特邀、`sort`），`uk_show_artist` 防重 |
| `user_follow_artist` | 用户关注艺人（`uk_user_artist` 去重） |
| `article_category` | 资讯分类（name 唯一） |
| `article` | 资讯（草稿/发布/下架三态；可选 `artist_id` 关联艺人；`published_at` 与状态联合索引） |
| `banner` | 首页 Banner（图片+跳转类型+目标 + `start_at`/`end_at` 定时窗口 + 状态/排序） |

**用户互动（收藏 / 订阅 / 消息 / 评价）**

| 表名 | 说明 |
|------|------|
| `favorite_group` | 用户自定义收藏分组（`uk_user_name` 同用户名不重） |
| `user_favorite` | 用户收藏演出（`uk_user_show` 一演出仅收藏一次，可跨分组移动） |
| `show_subscribe` | 演出开售提醒订阅（`notify_before_minutes` 提前 N 分钟 + `notified_pre`/`notified_open` 幂等标记） |
| `message` | 站内信主表（5 类：订单/开售/系统/互动/关注动态；`broadcast=1` 表示广播） |
| `user_message` | 用户-消息收件箱（`uk_user_msg`；`idx_user_unread_time` 加速未读列表） |
| `show_review` | 演出评价（`parent_id` 自关联实现一级评论+二级回复；评分仅一级；`like_count` / `reply_count` 冗余） |
| `show_review_image` | 评价晒图（多图按 `sort` 排序） |
| `show_review_like` | 评价点赞（`uk_review_user` 去重） |
| `show_review_report` | 评价举报（含 admin 处理状态/处理人/处理时间） |

---

## Redis 设计

| Key | 类型 | 说明 | TTL |
|-----|------|------|-----|
| `session:seats:{sessionId}` | Set | 可售座位 ID 集合(选座+派座共用的"已售/未售真相源") | 7 天 |
| `seat:info:{seatId}` | Hash | 座位详情（行 / 列 / 类型 / 区域） | 7 天 |
| `seat:lock:{sessionId}:{seatId}` | String | 座位锁(选座模式,value = userId) | 5 分钟 |
| `session:purchase:{sessionId}:{userId}` | String | 用户已购数量 | 7 天 |
| `session:area:price:{sessionId}:{areaId}` | Hash | 区域价格 + 售卖模式配置(price/originPrice/saleMode/singleTotal/coupleTotal),`reserveStock` 直接读 | 7 天 |
| `session:locked:{sessionId}` | String | 当前正在结算中（已锁座未支付）的座位数量 | 7 天 |
| `area:stock:single:{sessionId}:{areaId}` | String | **派座模式** — 区域单座剩余张数(原子 DECRBY) | 7 天 |
| `area:stock:couple:{sessionId}:{areaId}` | String | **派座模式** — 区域情侣对剩余对数 | 7 天 |
| `area:pool:single:{sessionId}:{areaId}` | ZSet | **派座模式** — 区域单座池;score=row\*100000+col,ZPOPMIN 取靠前 N 个 | 7 天 |
| `area:pool:couple:{sessionId}:{areaId}` | ZSet | **派座模式** — 区域情侣对池;member="leftId:rightId",取出 = 一对原子取出绝不拆散 | 7 天 |
| `rate:global:{method}:{window}` | String | 全局限流计数 | 动态 |
| `rate:user:{userId}:{method}:{window}` | String | 用户限流计数 | 动态 |
| `rate:ip:{ip}:{method}:{window}` | String | IP 限流计数 | 动态 |
| `blacklist:user:{userId}` | String | 用户黑名单 | 自定义 |
| `blacklist:ip:{ip}` | String | IP 黑名单 | 自定义 |

---

## 高并发设计要点

| 问题 | 方案 |
|------|------|
| 超卖(选座) | Redis Set `SREM` + 单座 SETNX 锁原子扣库存 + DB 层二次校验兜底 |
| 超卖(派座) | 两段式 — 同步 `DECR area:stock` 原子扣计数,异步 `ZPOPMIN area:pool` 取座,Lua 内做"不足回滚" |
| 情侣座保护 | warmup 时按 `type` 物理分池(single/couple),couple 池 member="left:right" 取出 = 原子成对;5 层防御(录入/池隔离/接口类型/选座完整性/退款成对) |
| 限购 | Lua 脚本原子 INCR + 阈值检查;派座按"实际张数"计算(情侣 = 对数*2) |
| 流量削峰 | `@RateLimit` 注解限流，全局 / 用户 / IP 三维度 |
| 批量锁座(选座) | Lua 脚本,任一失败全量回滚,不留半锁 |
| 派座任务恢复 | `seat_allocation_task` 持久化 + `task=SUCCESS` 与 INSERT 同事务;消费者崩溃由 `SeatAllocationRecoveryScheduler` 据 `allocated_seats` 区分回滚方式 |
| 订单超时 | RabbitMQ TTL + 死信队列 5 分钟触发;`OrderTimeoutScanScheduler` 每分钟扫 `expire_time+60s` 兜底,MQ 失败也能释放 |
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
            ┌──────────────┬───────┴───────┬──────────────┐
            │              │               │              │
   ┌────────▼─────┐ ┌──────▼──────┐ ┌──────▼──────┐ ┌─────▼──────┐
   │   MySQL 8    │ │   Redis 7   │ │  RabbitMQ 3 │ │  ES 8.13   │
   │  (主从可选)   │ │  (缓存/锁)  │ │ (事件/超时) │ │ (全文搜索) │
   └──────────────┘ └─────────────┘ └─────────────┘ └────────────┘
                            │
                    ┌───────▼───────┐
                    │     MinIO     │
                    │ (S3 兼容存储) │
                    └───────────────┘
```

---

## 容量评估与可观测性

**真实容量**(基于当前代码 + HikariCP 调到 50):

| 场景 | 可达 | 说明 |
|------|------|------|
| 浏览/详情 QPS | 数千 | Caffeine 本地缓存 + Redis,加机器线性扩展 |
| 持续下单 QPS | 单实例约 2000 | 瓶颈在 MySQL 写入;抢票路径已经异步解耦 |
| 单场万人抢票 | 需库存分片 + 读写分离 | 架构支持,见扩展方向 |

**可观测性**:

- 所有应用暴露 `/actuator/prometheus`(HTTP QPS / p95p99 延迟 / HikariCP 连接池 / JVM / RabbitMQ 速率)
- `docker-compose up -d` 同时起 Prometheus + Grafana,自动注入概览看板
- 每个响应携带 `X-Trace-Id` 头 + Result body 的 `traceId` 字段,便于排障

**分布式调度**:所有 `@Scheduled` 任务通过 `@SchedulerLock` (ShedLock + Redis) 互斥,多实例部署安全,只有抢到锁的实例真正跑,其他自动 failover。

---

## 扩展方向

- **真实支付**:实现 `PaymentGateway` 接口对接支付宝 / 微信
- **通知服务**:接入短信 / 推送,实现 `notification.queue` 消费者
- **微服务化**:admin / user / payment 拆分独立部署 + API Gateway
- **订单分表**:按 `session_id` / 月份分片
- **库存分片**:把单场次的座位 Set 拆成 N 个分片,消除 Redis 单 key 热点
- **读写分离**:演出/订单列表等读请求走从库
- **CDN**:演出海报等静态资源加速;上线时把 MinIO 切换为阿里云 OSS / AWS S3,只改 `minio.endpoint` / `accessKey` 即可

---

## License

[MIT](LICENSE)
