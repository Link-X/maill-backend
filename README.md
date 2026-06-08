# Ticket Booking System — Backend

![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.x-brightgreen)
![MySQL](https://img.shields.io/badge/MySQL-8.x-orange)
![Redis](https://img.shields.io/badge/Redis-7.x-red)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.x-ff6600)
![MinIO](https://img.shields.io/badge/MinIO-S3%20compatible-c72e49)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

[中文文档](README.zh.md)

A ticket booking backend designed for thousand-level sustained QPS and ten-thousand-level peak ticket-grab scenarios. Architecture-wise it covers all the core patterns (Redis Lua atomic seat lock, local cache + pub/sub invalidation, async order creation, MQ-driven timeout cancellation, distributed scheduling, optimistic-locked state machine); reaching the upper bound at production scale still needs horizontal scaling + inventory sharding. Features show management, **hybrid sale mode (user-pick seats + system-allocate seats)**, Redis inventory, order timeout, mock payment, venue check-in, partial refunds, full-text search, in-app messaging, reviews, monitoring, and more. Built as a Maven multi-module project for independent deployment.

> **Companion frontend repository**: [Link-X/maill-frontend](https://github.com/Link-X/maill-frontend)

---

## Features

- **Show Management** — CRUD for shows / sessions / seats; admin warms up seat inventory into Redis with one click
- **Category Management** — Dedicated `category` table + admin CRUD; shows link via `categoryId`, list/detail endpoints LEFT JOIN to return `categoryName` in a single call; user-side `/api/category/list` powers the home-page tabs
- **City & Address** — `city` table seeded with 30 major cities (GB/T administrative codes); shows include `cityCode` + `address`; list / detail / order / session-detail endpoints all return `cityName`; user-side home page can switch by city
- **Extend Fields** — `show` / `show_session` expose an `extend JSON` column so product can add display-only attributes without ALTER TABLE; conventions live in the frontend doc (not used in WHERE / indexes)
- **Venue Templates** — Define seat layout and default prices once on a room; sessions created with a `roomId` auto-copy all seats and price areas instantly; `/room/template` endpoint returns room + seats + areas in a single call
- **Image Upload** — Admin `/upload/image` endpoint backed by MinIO object storage (S3-compatible) for show posters, venue maps, etc.; returns an externally accessible URL
- **Artists** — `artist` master + `show_artist` many-to-many association; users follow / unfollow (`user_follow_artist`); show detail joins and embeds artist list; articles can be filtered by artist
- **Articles (News Center)** — `article_category` + `article` tables; three-state lifecycle (draft / published / offline); cover image, rich-text content, optional artist link; user-side list orders by category + published_at DESC
- **Home Banner** — `banner` table with image + jump target (show / artist / article / external URL); scheduled show/hide (`start_at` / `end_at`) + sort order; user-side `/api/banner/list` returns active banners in one call
- **Favorites with Groups** — `favorite_group` + `user_favorite`; user-defined groups (NULL = uncategorized); `(user_id, show_id)` unique, supports moving across groups
- **Open-sale Reminders** — Subscription is at show level, push is at session level. `SubscribeNotifier` scans every minute and groups by `(subscribe, date)` — sessions of the same show on the same day are merged into a single message (e.g. "3 sessions opening today: 18:00 / 20:30 / 22:00"), avoiding notification flooding for multi-leg tours. Per-session idempotency tracked in `show_subscribe_session_notify`
- **In-app Messages** — Decoupled `message` + `user_message` (5 categories: order / open-sale / system / interaction / follow-activity); supports unicast & broadcast; user-side list / unread count / mark-read / batch delete
- **Show Reviews** — One-level comments + nested replies (`parent_id` self-reference); 1-5 star rating on top-level only; image attachments, likes (deduped via `uk_review_user`), reports, admin moderation (hide / restore / delete); per-show `review_mode` (disabled / open / verified-attendees) + `avg_rating` / `review_count` denormalized counters
- **Reporting** — Admin `/api/admin/report/*` exposes 11 aggregate endpoints (overview / time series / by show / category / city / status / hour / session fill-rate / user / refund / cancellation); rolling time windows (1d/7d/30d/90d) or custom; results cached in Redis for 5 minutes. The `order` table now tracks `refund_amount` cumulatively and a `cancel_reason` to distinguish user-cancelled vs. timeout-cancelled
- **Full-text Search** — Elasticsearch 8.x indexes three doc types (show / artist / article) asynchronously; write paths publish to `search.sync.queue`, `SearchSyncConsumer` upserts ES, `IndexInitializer` creates indices idempotently on startup; degrades gracefully when ES is unavailable
- **Booking Core (Async Order Creation)** — `/api/order/submit/by-seats` synchronously does only "validate + purchase-limit + Lua batch lock + pre-generate orderNo + publish MQ", returns immediately (~5ms). Actual `INSERT order` is consumed by `OrderCreateConsumer`; the frontend polls `/api/order/createStatus` with the orderNo until SUCCESS/FAILED. Decouples seat-lock from DB writes — under load the per-instance QPS ceiling jumps from a few hundred to a few thousand
- **Hybrid Sale Mode (pick-seats + system-allocate coexist)** — `seat_area.sale_mode` is per-area: each area in the same session can be independently configured as "user picks seats" or "system allocates seats" (typical large concert pattern — VIP rows let users pick, grandstand sections are system-allocated). Allocation flow uses `/api/order/submit/by-area` accepting `{area, ticketType, quantity}`; backend synchronously DECRs `area:stock` (atomic counter), then asynchronously `ZPOPMIN`s from `area:pool` and creates the order
- **Couple-seat Protection (5-layer defense)** — At warmup, `seat.type` **physically partitions** singles and couples into two pools (`area:pool:single` / `area:pool:couple`). `ticketType=1` only pops from the single pool; `ticketType=2` only pops from the couple pool whose members are `"leftId:rightId"` strings — a `ZPOPMIN` of a single member atomically returns both seats of a pair. **It is structurally impossible to allocate one seat of a couple pair to a single-seat buyer.** Combined with paired-entry validation, completeness check on pick-mode, and paired-refund constraint — 5 layers of defense in total
- **Allocation Task Persistence + Self-healing** — The allocate flow spans Redis + MySQL ("DECR stock → publish MQ → ZPOPMIN pool → INSERT order → task=SUCCESS"); all intermediate state lives in `seat_allocation_task`, and `task=SUCCESS` is committed in the **same DB transaction** as the INSERT to make them atomic. `SeatAllocationRecoveryScheduler` scans PENDING tasks older than 2 min every minute; it inspects `allocated_seats` to choose between "rollback stock only" vs "rollback stock + return to pool" — surviving consumer crashes
- **Order Timeout Safety-net Scan** — `OrderTimeoutScanScheduler` runs every minute scanning orders where `status=0 AND expire_time < now-60s`, running in parallel with the MQ TTL queue. Even if MQ delivery fails, seats are eventually released
- **Oversell Prevention** — Redis Set atomic `SREM` deduction + DB-level safety check
- **Order Timeout** — RabbitMQ TTL + dead-letter queue, cancels order and releases inventory exactly 5 minutes after creation
- **Async Events** — After payment, RabbitMQ Fanout fan-out triggers ticket generation, DB inventory sync, and notification (reserved) in parallel
- **Refunds** — Full-order and per-ticket refunds; partially-refunded orders (status 5) can continue to refund remaining unused tickets
- **Session State Machine (auto-flow)** — `SessionLifecycleScheduler` flips `0 → 1` (auto-warmup Redis + open sale) at `openSaleTime` and `0/1 → 2` (ended) at `endTime`. Admin only configures data, no manual publish/warmup needed. Optimistic-lock on state transitions
- **Local Cache + pub/sub Invalidation** — Seat structure / price / show / city cached in Caffeine to keep hot sessions from hitting the DB on refresh. Writes broadcast via Redis pub/sub to invalidate Caffeine on all instances within milliseconds (multi-instance consistency). Session info (with mutable `status`) bypasses cache and reads DB directly to avoid stale state under multi-instance deploys
- **Distributed Scheduling (ShedLock)** — All `@Scheduled` tasks are mutually-exclusive across JVM instances via Redis-backed ShedLock: only one instance executes per tick; if it crashes another picks up next tick (failover). Replaces single-instance scheduling
- **Observability (Prometheus + Grafana)** — All apps expose `/actuator/prometheus`; the bundled `docker-compose` ships Prometheus + Grafana with a preloaded overview dashboard (JVM heap, HTTP QPS, p95/p99 latency, HikariCP pool, 5xx error rate, RabbitMQ rates)
- **Annotation Rate Limiting** — `@RateLimit` annotation supports GLOBAL / USER / IP three-dimensional fixed-window rate limiting + blacklist interception
- **Parameter Validation** — Admin write endpoints use dedicated `*Request` DTOs with `@Valid` (clients cannot inject `id` / `status` / `createTime` etc.); global exception handler returns unified friendly errors
- **Trace ID** — `TraceIdFilter` issues a per-request trace id, pushes it to MDC, returns it via the `X-Trace-Id` response header and the `traceId` field on every Result body — perfect for client/server log correlation
- **API Docs** — SpringDoc OpenAPI auto-generates Swagger UI at `/swagger-ui.html`; no hand-maintained interface docs
- **Check-in Verification** — Dual-channel: QR code or ticket number
- **JWT Auth** — `@NoLogin` annotation marks public endpoints; all others require authentication by default

---

## Tech Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Framework | Spring Boot | 3.2.x / JDK 17 |
| ORM | MyBatis | 3.5.x |
| Cache / Lock | Redis + Redisson | 7.x / 3.27.x |
| Local Cache | Caffeine | (managed by Spring Boot) |
| Distributed Scheduling | ShedLock | 5.13.x |
| Message Queue | RabbitMQ | 3.x |
| Database | MySQL | 8.x |
| Full-text Search | Elasticsearch (Java API Client) | 8.13.x |
| Object Storage | MinIO (S3-compatible) | 8.5.x SDK |
| Auth | Spring Security + JJWT | 0.12.x |
| Monitoring | Micrometer + Prometheus + Grafana | 1.x / 2.51 / 10.4 |
| Build | Maven | — |

---

## Module Structure

```
maill-backend/
├── common/      # Utilities: response wrapper, exceptions, Snowflake ID, RedisKeys, @RateLimit AOP, blacklist, TraceIdFilter
│   └── es/      # Elasticsearch client config + index mappings + IndexInitializer (idempotent index creation on startup)
├── core/        # Core business: entities, Mappers, Services, MQ Producer/Consumer, JsonMapTypeHandler
│   ├── cache/     # CacheInvalidationBroadcaster/Listener: Redis pub/sub for multi-instance Caffeine invalidation
│   ├── config/    # ShedLockConfig (Redis lock provider), CacheInvalidationConfig (pub/sub registration), etc.
│   └── scheduler/ # Cron tasks (all guarded by @SchedulerLock):
│                  #   SubscribeNotifier (open-sale reminders, batches sessions of same day)
│                  #   SessionLifecycleScheduler (status 0→1 auto-warmup + 0/1→2 ended)
│                  #   ArticleViewFlushScheduler (Redis view-counter → DB flush)
│                  #   SeatAllocationRecoveryScheduler (recover timed-out allocation tasks: stock + pool repair)
│                  #   OrderTimeoutScanScheduler (safety-net order cancellation; releases seats even when MQ fails)
├── admin/       # Admin REST API (port 8081) + Request DTOs + AdminAuthInterceptor
├── user/        # User  REST API (port 8082) + LoginCheckInterceptor
├── payment/     # Payment module (port 8083, reserved)
├── sql/
│   └── schema.sql
└── docker-compose.yml
```

**Service layering (CQRS-lite)** — single-responsibility split between writes and reads:

| Service | Responsibility |
|---------|---------------|
| `ShowService` | Show CRUD + artist relation upkeep + ES sync events |
| `SessionService` | Session CRUD + publish + seat-map aggregate query |
| `CategoryService` / `CityService` | Category / city (city is read-only) |
| `RoomService` | Venue template + `copyToSession` (copies seats / price areas) |
| `OrderCommandService` | Place / cancel / refund (transactional writes); 3-layer split — pick-mode entry / allocate-mode entry / pure-create core (`createOrderInternal` is `@Transactional`, invoked via `self` proxy) |
| `OrderQueryService` | Single / list queries + OrderStatusResponse assembly (batch prefetch, avoids N+1) |
| `SeatInventoryService` / `PurchaseLimitService` | Redis inventory and purchase limits (pick-mode SET+lock + allocate-mode stock counter & single/couple pools) |
| `SeatAllocationService` | Two-phase allocation primitives: `reserveStock` (atomic DECR) + `allocate` (ZPOPMIN); couple pool members are atomically pair-shaped |
| `ArtistService` / `ArticleService` / `ArticleCategoryService` / `BannerService` | Artist / article / article-category / banner (with ES sync) |
| `FavoriteService` / `SubscribeService` | Favorites & groups; open-sale reminder subscriptions |
| `MessageService` | In-app messaging: unicast / broadcast / read / delete |
| `ReviewService` | Review publish / reply / like / report / rating aggregation (`avg_rating` / `review_count` maintained) |
| `SearchService` | ES multi-index query + per-user search history |
| `StorageService` | MinIO upload |

Dependency chain:

```
common ← core ← admin
                 user
                 payment
```

---

## Quick Start

### Prerequisites

- JDK 17+
- Maven 3.8+
- Docker & Docker Compose

### 1. Start infrastructure

```bash
docker-compose up -d
```

Starts MySQL 8 (3306), Redis 7 (6379), RabbitMQ 3 (5672, management UI on 15672), MinIO (9000 API / 9001 console), Elasticsearch 8.13 (9200), Prometheus (9090), and Grafana (3000). `sql/schema.sql` is executed automatically on first run; the MinIO `image` bucket (configured via `minio.bucket` in `application-dev.yml`) is auto-created with a public-read policy the first time admin starts; the three ES indices (show / artist / article) are created idempotently on startup by `IndexInitializer`; Grafana auto-provisions the Prometheus datasource and the "Ticket System Overview" dashboard on first run — no manual setup required.

> **RabbitMQ Management UI**: http://localhost:15672 (guest / guest)
> **MinIO Console**: http://localhost:9001 (minioadmin / minioadmin123)
> **Elasticsearch**: http://localhost:9200 (no auth, single-node dev mode)
> **Prometheus**: http://localhost:9090 (scrapes `/actuator/prometheus` from each Spring Boot app)
> **Grafana**: http://localhost:3000 (admin / admin, preloaded "Ticket System Overview" dashboard)

### 2. Build

```bash
mvn compile -q
```

### 3. Run modules

```bash
# Admin service (8081)
mvn spring-boot:run -pl admin

# User service (8082)
mvn spring-boot:run -pl user
```

The default profile is `dev`. Database password is `root123`. Edit each module's `application-dev.yml` to change.

After starting:
- **Swagger UI**: http://localhost:8081/swagger-ui.html (admin) / http://localhost:8082/swagger-ui.html (user)
- Every response carries an `X-Trace-Id` header and a `traceId` field in the Result body — paste this id when reporting issues so the backend can locate the log.

### 4. Seed load-test data (optional)

```bash
# Requires jq: brew install jq
bash docs/seed-data.sh
```

Creates 1 venue template (20 × 20 seats, VIP front section), 5 shows, 15 sessions, then publishes and warms up all sessions into Redis. Total: 6 000 bookable seats ready for load testing.

---

## Core Booking Flow (Async Order Creation)

### Pick-seats mode — POST /api/order/submit/by-seats

```
User submits seat selection — POST /api/order/submit/by-seats
    │
    ├─ @RateLimit blacklist / IP / user / global check (AOP, first to intercept)
    ├─ Session validation (status / openSaleTime / endTime)
    ├─ Purchase-limit check & increment (Redis)
    ├─ Lua batch seat lock (full rollback on any failure)
    ├─ Pre-generate orderNo (Snowflake)
    ├─ Set Redis pending key: order:create:pending:{orderNo} = "PROCESSING" (TTL 60s)
    ├─ Publish OrderCreateMessage to order.create.queue
    └─ Return immediately { orderNo, status: "PROCESSING" }   (~5ms)
                │
                ├─────────────────── (Async) ──────────────────┐
                │                                              │
   Frontend polls /api/order/createStatus?orderNo=...   OrderCreateConsumer
   every ~600ms until SUCCESS / FAILED                  consumes the message
                │                                              │
                │                                  ├─ Idempotency: selectByOrderNo,
                │                                  │   if exists → return
                │                                  ├─ Oversell safety check (DB)
                │                                  ├─ Load seats, validate couple-seats
                │                                  ├─ Compute total price (from Redis)
                │                                  ├─ INSERT order + INSERT order_item (TX)
                │                                  └─ afterCommit:
                │                                       consumeSeat()
                │                                       sendTimeoutMessage()
                │                                              │
   ┌────────────┴────────────┐                                 │
   │                         │                                 │
SUCCESS                    FAILED                              │
DB hit on orderNo          Redis pending = FAILED:reason       │
→ returns OrderStatusResponse  → frontend shows error          │
   │
User clicks Pay → POST /api/payment/create
   │
   ├─ Create payment record
   ├─ Order status → PAID
   └─ Send payment event (Fanout)
              │
    ┌─────────┼──────────┐
    │         │          │
Generate   Sync DB    Send notification
Tickets   inventory   (reserved)
(async)    (async)

   --- If user doesn't pay within 5 min ---
   Timeout message routed via DLX → order.cancel.queue → cancel + release inventory
```

> **Why async**: IF `submit`  synchronous  stuck at ~50-100ms per request (DB INSERT bottleneck). By only locking seats synchronously and offloading INSERT to a consumer, `submit` returns in ~5ms. Frontend feels "success" instantly, and per-instance QPS ceiling lifts from ~500 to ~2000+.
>
> **Failure handling**: If the consumer hits a business exception (seat already taken in oversell-safety check, area price missing) — `OrderCommandService` releases seats + rolls back purchase count + the consumer marks the pending key as FAILED. Frontend's next poll sees FAILED and shows the reason. If a system exception (DB unavailable) — the consumer rethrows so RabbitMQ retries (3 times default); the user sees PROCESSING and eventually SUCCESS or FAILED.

### Allocate-seats mode — POST /api/order/submit/by-area

```
User picks {area, ticketType, quantity} — POST /api/order/submit/by-area
    │
    ├─ @RateLimit blacklist / IP / user / global
    ├─ Session validation
    ├─ Purchase-limit check & increment (couple counted as ×2 seats)
    ├─ allocationService.reserveStock — atomic DECR area:stock:{single|couple} (Lua)
    │       └─ Validates saleMode=2 + ticket type availability; insufficient stock fails fast
    ├─ Pre-generate orderNo
    ├─ INSERT seat_allocation_task = PENDING        ← persistent state for scheduler-based recovery
    ├─ Set Redis pending key
    ├─ Publish OrderAllocateMessage to order.allocate.queue
    └─ Return immediately { orderNo, status: "PROCESSING" }
                │
                ├─────────────────── (Async) ──────────────────┐
                │                                              │
   Frontend polls /api/order/createStatus               OrderAllocateConsumer
                │                                              │
                │                                  ├─ Idempotency: if DB has the order → CAS task=SUCCESS + delete pending + return
                │                                  ├─ task=SUCCESS/ROLLED_BACK → short-circuit or throw
                │                                  ├─ allocationService.allocate — Lua ZPOPMIN N members from the pool
                │                                  │       (single pool: member=seatId; couple pool: member="left:right")
                │                                  ├─ Persist task.allocated_seats immediately (gives scheduler a recovery flag)
                │                                  ├─ self.createOrderInternal (@Transactional):
                │                                  │       INSERT order + items + task=SUCCESS in the same TX
                │                                  └─ afterCommit:
                │                                       consumeAllocatedSeat() — SREM session:seats
                │                                       sendTimeoutMessage()
                │
   ┌────────────┴────────────┐
   │                         │
SUCCESS                    FAILED
DB hit on orderNo          Redis pending = FAILED:reason
→ returns OrderStatusResponse  → frontend shows error
   │
   └─ Subsequent flow is identical to pick-seats (payment → tickets / inventory sync / notification)
```

> **Why two-phase stock-deduction**: the hot path only touches the `area:stock` counter (atomic DECR), never the ZSET — fast-failing on insufficient stock is ultra-cheap, and the actual seat picking is deferred to the async consumer.
>
> **Couple-seat protection (core)**: At warmup, `seat.type` partitions singles (`type=1`) and couples (`type=2/3` pairs) into **physically separate pools**. The single pool **never contains couple-seat IDs**, so `ZPOPMIN` against it cannot return one. The couple pool's member is `"leftId:rightId"` — popping a single member atomically yields both seats of a pair, which is **structurally impossible to split**.
>
> **Crash recovery**: `seat_allocation_task` persists every intermediate step. The scheduler scans PENDING tasks older than 2 min and inspects `allocated_seats` — if empty (ZPOPMIN never ran) it only re-incs stock; if non-empty it both re-incs stock and re-`ZADD`s seats. Because `task=SUCCESS` shares the same DB transaction as the INSERT, a crash after INSERT but before status update is also safe (consumer's idempotency path detects the DB order and CAS-promotes the task).

---

## Refund Flow

```
Order status: 1 (PAID) or 5 (PARTIAL_REFUND)
    │
    ├─ Full cancel  POST /api/order/cancel
    │       └─ Find all unused tickets → doRefund → status → REFUNDING (3)
    │
    └─ Per-ticket   POST /api/order/refundTicket
            ├─ Couple-seat single-ticket refund blocked (must refund the pair together) → business exception
            └─ Validate ticket is unused → doRefund → status → REFUNDING (3)
                        │
              MQ consumer processes refund result
                        │
            ┌───────────┴───────────┐
            │                       │
    Unused tickets remain       All tickets refunded
    status → PARTIAL_REFUND (5) status → REFUNDED (4)
```

> **Release dispatch**: `releaseSeatsByMode` chooses the release path per-seat based on its area's `sale_mode` — pick-mode area: return to `session:seats` SET + delete `seat:lock` + DECR locked counter; allocate-mode area additionally returns seats to the corresponding `area:pool` + INCRs `area:stock`. Couple pairs are always returned as a pair, never split.

---

## Order Status Reference

| Status | Meaning |
|:------:|---------|
| 0 | Pending payment |
| 1 | Paid |
| 2 | Cancelled |
| 3 | Refunding |
| 4 | Refunded |
| 5 | Partially refunded |

---

## Message Queue Design

```
Async Order Creation — Pick-seats mode (Direct):
  order.create.exchange ──→ order.create.queue ──→ OrderCreateConsumer (INSERT order + items + send timeout MQ)
                              (3-retry built-in; failure → marks Redis pending key as FAILED)

Async Order Creation — Allocate-seats mode (Direct):
  order.allocate.exchange ──→ order.allocate.queue ──→ OrderAllocateConsumer
                              (ZPOPMIN pool + INSERT order + task=SUCCESS in same TX; failure rolls back stock + pool)

Order Timeout (TTL + Dead Letter):
  order.timeout.exchange ──→ order.timeout.queue (TTL 5 min)
                                      │ expires
  order.dead.exchange    ──→ order.cancel.queue ──→ OrderTimeoutConsumer (cancel order)

Payment Success (Fanout):
  payment.success.exchange ──→ ticket.generate.queue  ──→ generate tickets
                           ──→ inventory.sync.queue   ──→ sync seat.status = SOLD
                           ──→ notification.queue     ──→ notify (reserved)

Refund (Direct):
  refund.exchange ──→ refund.queue ──→ RefundConsumer (final order state from remaining unused tickets)

Search Sync (Direct, fan-in after show / artist / article writes):
  search.sync.exchange ──→ search.sync.queue ──→ SearchSyncConsumer (upsert / delete ES doc)
```

---

## Annotation Rate Limiting

Stack `@RateLimit` annotations on any Controller method — AOP intercepts automatically, no business code changes needed:

```java
@RateLimit(type = LimitType.BLACKLIST)
@RateLimit(type = LimitType.IP,     limit = 20,  window = 60)
@RateLimit(type = LimitType.USER,   limit = 5,   window = 60)
@RateLimit(type = LimitType.GLOBAL, limit = 50,  window = 1,  message = "System busy")
@PostMapping("/submit")
public Result<?> submit(...) { }
```

**Check order**: Blacklist → IP → User → Global. Earlier checks are cheaper to evaluate.

---

## Database Design

30 tables in total, grouped by business domain:

**Core transactional (show / session / seat / order / payment)**

| Table | Description |
|-------|-------------|
| `user` | Users, BCrypt passwords |
| `user_role` | Roles: USER / ADMIN |
| `category` | Show categories (`name` unique; `sort`/`status` for ordering and enable/disable; `idx_status_sort` index) |
| `city` | Cities (GB/T administrative codes, `code` unique); 30 major cities seeded, no write endpoint |
| `show` | Shows; `category_id` / `city_code` link to category and city; `address` full street address; `extend` JSON; `review_mode` review-mode + `avg_rating` / `review_count` denormalized rating stats; `open_sale_time` for reminders; indexes `idx_name` / `idx_venue` / `idx_category_id` / `idx_city_code` / `idx_open_sale_time` |
| `show_session` | Sessions; `room_id` links the venue template; `limit_per_user` cap; `extend` JSON for ad-hoc display fields |
| `seat` | Seat master table (`type` 1=single / 2=couple-left / 3=couple-right; `pair_seat_id` mutually-pointing); real-time inventory lives in Redis, `status` synced async after payment |
| `seat_area` | Per-session seat price areas; `sale_mode` (1=pick / 2=allocate), `single_total` / `couple_total` (allocate-mode statistics), `allocate_strategy` (allocation strategy) |
| `seat_allocation_task` | Allocation tasks (intermediate state persistence); columns: `order_no` UNIQUE / `ticket_type` / `quantity` / `status` (0 pending, 1 success, 2 failed, 3 rolled-back) / `allocated_seats` CSV; `idx_status_create` for scheduler timeout scans |
| `order` | Orders; `refund_amount` (cumulative) and `cancel_reason` (user vs. timeout) drive reports; indexes `idx_status_expire` / `idx_create_time` (for reporting time-window scans) |
| `order_item` | Order lines with price snapshot |
| `payment` | Payment records |
| `ticket` | Tickets with 8-char friendly ticket number (excludes O/0/I/1) + UUID QR code |
| `room` | Venue template (name, dimensions) |
| `room_seat` | Seat layout template for a room |
| `room_area` | Default price areas for a room (copied to `seat_area` on session creation) |

**Operational content (artists / articles / banner)**

| Table | Description |
|-------|-------------|
| `artist` | Artist master (real / stage name, avatar, tags, `social_links` JSON, denormalized `follow_count` / `show_count`) |
| `show_artist` | Show-artist many-to-many (`role` lead / director / guest, `sort`); `uk_show_artist` deduplicates |
| `user_follow_artist` | User → artist follow (`uk_user_artist`) |
| `article_category` | Article category (`name` unique) |
| `article` | Article (draft / published / offline; optional `artist_id` link; composite index on `published_at` + status) |
| `banner` | Home banner (image + jump type/target + `start_at` / `end_at` time-window + status + sort) |

**User engagement (favorites / subscriptions / messages / reviews)**

| Table | Description |
|-------|-------------|
| `favorite_group` | User-defined favorite groups (`uk_user_name`) |
| `user_favorite` | Show favorites (`uk_user_show` — one favorite per show, movable across groups) |
| `show_subscribe` | Open-sale reminder subscription (`notify_before_minutes` + `notified_pre` / `notified_open` idempotency flags) |
| `message` | Message master (5 types: order / open-sale / system / interaction / follow-activity; `broadcast=1` for fan-out) |
| `user_message` | User inbox (`uk_user_msg`; `idx_user_unread_time` accelerates unread feed) |
| `show_review` | Show reviews (`parent_id` self-join: one-level comment + nested reply; rating top-level only; `like_count` / `reply_count` denormalized) |
| `show_review_image` | Review image attachments (ordered by `sort`) |
| `show_review_like` | Review likes (`uk_review_user` deduplicates) |
| `show_review_report` | Review reports with admin handling state / handler / handle time |

---

## Redis Design

| Key | Type | Description | TTL |
|-----|------|-------------|-----|
| `session:seats:{sessionId}` | Set | Available seat ID pool (shared by both modes as "sold/unsold source of truth") | 7 days |
| `seat:info:{seatId}` | Hash | Seat details: row / col / type / area | 7 days |
| `seat:lock:{sessionId}:{seatId}` | String | Seat lock (pick-mode only, value = userId) | 5 min |
| `session:purchase:{sessionId}:{userId}` | String | Per-user purchase count | 7 days |
| `session:area:price:{sessionId}:{areaId}` | Hash | Area price + sale-mode config (price / originPrice / saleMode / singleTotal / coupleTotal); `reserveStock` reads directly | 7 days |
| `session:locked:{sessionId}` | String | Count of seats currently locked in checkout | 7 days |
| `area:stock:single:{sessionId}:{areaId}` | String | **Allocate-mode** — area single-seat remaining count (atomic DECRBY) | 7 days |
| `area:stock:couple:{sessionId}:{areaId}` | String | **Allocate-mode** — area couple-pair remaining count | 7 days |
| `area:pool:single:{sessionId}:{areaId}` | ZSet | **Allocate-mode** — area single-seat pool; score = row\*100000+col; ZPOPMIN returns the front N | 7 days |
| `area:pool:couple:{sessionId}:{areaId}` | ZSet | **Allocate-mode** — area couple-pair pool; member = `"leftId:rightId"`; popping a member atomically returns one pair, structurally impossible to split | 7 days |
| `rate:global:{method}:{window}` | String | Global rate-limit counter | dynamic |
| `rate:user:{userId}:{method}:{window}` | String | User rate-limit counter | dynamic |
| `rate:ip:{ip}:{method}:{window}` | String | IP rate-limit counter | dynamic |
| `blacklist:user:{userId}` | String | User blacklist | custom |
| `blacklist:ip:{ip}` | String | IP blacklist | custom |

---

## Concurrency Design

| Problem | Solution |
|---------|----------|
| Oversell (pick-mode) | Redis Set `SREM` + per-seat SETNX lock atomic deduction + DB safety check |
| Oversell (allocate-mode) | Two-phase — synchronous `DECR area:stock` (atomic counter), async `ZPOPMIN area:pool`; Lua "insufficient-then-rollback" inside the pool script |
| Couple-seat protection | Warmup physically partitions pools by `type` (single / couple); couple-pool member = "left:right" pops atomically as a pair; 5-layer defense (entry validation / pool isolation / ticket-type enforcement / pick-mode completeness check / paired-refund constraint) |
| Purchase limit | Lua atomic INCR + threshold check; allocate-mode counts "actual seat count" (couple = pairs × 2) |
| Traffic spike | `@RateLimit` annotation limiting: global / user / IP three dimensions |
| Batch seat lock (pick-mode) | Lua script — full rollback on any failure, no partial locks |
| Allocation task recovery | `seat_allocation_task` persistence + `task=SUCCESS` shares the INSERT transaction; on consumer crash, `SeatAllocationRecoveryScheduler` chooses rollback path by `allocated_seats` |
| Order timeout | RabbitMQ TTL + dead-letter queue (5 min); `OrderTimeoutScanScheduler` scans `expire_time+60s` as a safety net — works even when MQ is down |
| Post-payment decoupling | RabbitMQ Fanout — ticket / inventory / notification processed async in parallel |
| Blacklist | Redis key storage, checked by AOP first, does not consume rate-limit counters |

---

## Security

- Passwords stored with BCrypt
- JWT authentication (30-minute expiry); `@NoLogin` marks public endpoints
- IDOR protection: order endpoints verify `order.userId == current user`
- SQL injection prevention via MyBatis `#{}` parameterized queries
- Server-side price recalculation — client-supplied amounts are never trusted
- Parameter validation: `@Valid` + `GlobalExceptionHandler` unified error handling

---

## Deployment Architecture

```
                      ┌──────────────────┐
                      │      Nginx       │
                      │  (Reverse Proxy) │
                      └────────┬─────────┘
                               │
                  ┌────────────┴────────────┐
                  │                         │
         ┌────────▼────────┐      ┌────────▼────────┐
         │  admin : 8081   │      │  user  : 8082   │
         │   Admin API     │      │    User API     │
         └────────┬────────┘      └────────┬────────┘
                  │                         │
                  └────────────┬────────────┘
                               │
            ┌──────────────┬───────┴───────┬──────────────┐
            │              │               │              │
   ┌────────▼─────┐ ┌──────▼──────┐ ┌──────▼──────┐ ┌─────▼──────┐
   │   MySQL 8    │ │   Redis 7   │ │  RabbitMQ 3 │ │  ES 8.13   │
   │ (replication │ │ (cache/lock)│ │ (events/TTL)│ │ (search)   │
   │  optional)   │ │             │ │             │ │            │
   └──────────────┘ └─────────────┘ └─────────────┘ └────────────┘
                            │
                    ┌───────▼───────┐
                    │     MinIO     │
                    │ (S3 storage)  │
                    └───────────────┘
```

---

## Capacity & Observability

**Realistic capacity** (with current code + HikariCP tuned to 50):

| Scenario | Achievable | Note |
|----------|-----------|------|
| Browsing / detail QPS | several thousand | Caffeine local cache + Redis, scales linearly with instances |
| Sustained order QPS | ~2000/instance | bottleneck shifts to MySQL writes; ticket-grab path is async-decoupled |
| Single-session burst (10k concurrent grab) | needs inventory sharding + read-replica | architecture supports it, see Roadmap |

**Observability**:

- All apps expose `/actuator/prometheus` (HTTP QPS / p95p99 latency / HikariCP pool / JVM / RabbitMQ rates)
- `docker-compose up -d` boots Prometheus + Grafana with an auto-provisioned overview dashboard
- Every response carries `X-Trace-Id` + `traceId` in the body for log correlation

**Distributed scheduling**: All `@Scheduled` tasks are protected by `@SchedulerLock` (ShedLock + Redis). Safe to deploy multiple instances — only one runs the cron tick, the rest failover automatically.

---

## Roadmap

- **Real payment gateways** — implement `PaymentGateway` for Alipay / WeChat Pay
- **Notification service** — integrate SMS / push, implement `notification.queue` consumer
- **Microservices** — split admin / user / payment into independent services behind an API Gateway
- **Order Sharding** — partition the order table by `session_id` / month
- **Inventory Sharding** — split the per-session seat Set into N shards to remove the Redis single-key hotspot
- **Read/Write Splitting** — read replicas for show / order list endpoints
- **CDN** — offload show poster and static assets; swap MinIO for Aliyun OSS / AWS S3 in production by changing `minio.endpoint` / `accessKey` only

---

## License

[MIT](LICENSE)
