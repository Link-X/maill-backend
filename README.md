# Ticket Booking System — Backend

![Java](https://img.shields.io/badge/Java-17-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.x-brightgreen)
![MySQL](https://img.shields.io/badge/MySQL-8.x-orange)
![Redis](https://img.shields.io/badge/Redis-7.x-red)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.x-ff6600)
![MinIO](https://img.shields.io/badge/MinIO-S3%20compatible-c72e49)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

[中文文档](README.zh.md)

A ticket booking backend designed for thousand-level sustained QPS and ten-thousand-level peak ticket-grab scenarios. Architecture-wise it covers all the core patterns (Redis Lua atomic seat lock, local cache + pub/sub invalidation, async order creation, MQ-driven timeout cancellation, distributed scheduling, optimistic-locked state machine); reaching the upper bound at production scale still needs horizontal scaling + inventory sharding. Features show management, seat selection, Redis inventory, order timeout, mock payment, venue check-in, partial refunds, full-text search, in-app messaging, reviews, monitoring, and more. Built as a Maven multi-module project for independent deployment.

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
- **Booking Core (Async Order Creation)** — `/api/order/submit` synchronously does only "validate + purchase-limit + Lua batch lock + pre-generate orderNo + publish MQ", returns immediately (~5ms). Actual `INSERT order` is consumed by `OrderCreateConsumer`; the frontend polls `/api/order/createStatus` with the orderNo until SUCCESS/FAILED. Decouples seat-lock from DB writes — under load the per-instance QPS ceiling jumps from a few hundred to a few thousand
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
| `OrderCommandService` | Place / cancel / refund (transactional writes) |
| `OrderQueryService` | Single / list queries + OrderStatusResponse assembly (batch prefetch, avoids N+1) |
| `SeatInventoryService` / `PurchaseLimitService` | Redis inventory and purchase limits |
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

## API Overview

> **Detailed fields, request bodies, response shapes, and error codes** are in Swagger UI (after starting the service):
> - User:  http://localhost:8082/swagger-ui.html
> - Admin: http://localhost:8081/swagger-ui.html
>
> The table below only gives a "what categories exist" overview, to avoid the README drifting away from the code.

### User Service (:8082)

| Module | Path prefix | Main capabilities |
|--------|------------|-------------------|
| Auth | `/api/auth/*` | Register / login (returns JWT) |
| Category / City (home tabs) | `/api/category` `/api/city` | Enabled lists sorted by `sort` |
| Banner | `/api/banner/list` | Home-page carousel (time-window + sort filtered) |
| Shows | `/api/show/*` | List (name / categoryId / cityCode / venue filters) / detail; ShowVO carries categoryName / cityName / address / extend / artists / reviewMode / avgRating |
| Sessions | `/api/session/*` | List / seat-map detail (includes show + city + address) |
| Artists | `/api/artist/*` | List / detail / follow / unfollow / follow status / my follows |
| Articles | `/api/article/*` `/api/article-category/list` | List (by category / by artist) / detail / category list |
| Search | `/api/search/*` | Multi-index search (show / artist / article / all) + per-user history (add / clear) |
| Favorites | `/api/favorite/*` | Add / remove / move-across-groups / check / list + group CRUD |
| Subscribe | `/api/subscribe/*` | Open-sale reminder subscribe / unsubscribe / check / list |
| Messages | `/api/message/*` | Inbox / unread count / mark read / mark all read / delete |
| Reviews | `/api/review/*` | Publish top-level / reply / list / nested replies / like / report / my reviews / permission check |
| Orders | `/api/order/*` | Lock & create / cancel / single-ticket refund / my orders / detail |
| Payment | `/api/payment/*` | Pay an order |
| Verification | `/api/verify/*` | QR / ticket-number check-in |
| Upload | `/api/upload/*` | User-side image upload (review attachments) to MinIO |

### Admin Service (:8081)

| Module | Path prefix | Main capabilities |
|--------|------------|-------------------|
| Auth | `/api/admin/auth/*` | Admin register / login (requires `ADMIN_INVITE_CODE`) |
| Categories | `/api/admin/category/*` | CRUD; deleting a referenced category returns 1012 |
| Cities (read-only) | `/api/admin/city/list` | Dropdown source for the show form; seeded by schema.sql |
| Venue templates | `/api/admin/room/*` | Room CRUD + seat template + default price areas + `/template` aggregate |
| Image upload | `/api/admin/upload/image` | Multipart upload to MinIO, returns external URL |
| Shows | `/api/admin/show/*` | Show CRUD with strict DTO validation; supports `reviewMode` / `openSaleTime` / `artistIds` / `artistRoles` to maintain show-artist relations in one call |
| Sessions | `/api/admin/session/*` | Session CRUD + `/{id}/publish`; passing `roomId` auto-copies seats + prices |
| Seats (manual) | `/api/admin/seat/*` | Batch seats / price areas / Redis warmup when not using a room template |
| Artists | `/api/admin/artist/*` | Artist CRUD + status toggle |
| Article categories | `/api/admin/article-category/*` | Article-category CRUD (in-use returns 1042; duplicate name 1041) |
| Articles | `/api/admin/article/*` | Save (draft) / publish / take offline / list / delete |
| Banner | `/api/admin/banner/*` | Banner CRUD + status toggle + sort |
| Messages | `/api/admin/message/*` | Send unicast / broadcast / list (pushes to user inbox) |
| Review moderation | `/api/admin/review/*` | Listing (incl. reported) / hide / restore / delete + report queue and handling |
| Orders | `/api/admin/order/*` | Single / list (filter by showId / sessionId / orderNo / status / time range) |
| Monitor | `/api/admin/monitor/dashboard` | Real-time seat counts (total / available / sold) |
| **Reports** | `/api/admin/report/*` | 11 aggregate endpoints: overview / time series / by show, category, city / status & hour distributions / session fill-rate / users / refunds / cancellations (Redis 5-min cache) |

---

<details>
<summary>Legacy full API tables (collapsed; field-level docs live in Swagger now)</summary>

#### Auth

| Method | Path | Description | Auth |
|--------|------|-------------|:----:|
| POST | `/api/auth/register` | Register | ✗ |
| POST | `/api/auth/login` | Login, returns JWT | ✗ |

#### Categories / Cities (home-page tabs)

| Method | Path | Description | Auth |
|--------|------|-------------|:----:|
| GET  | `/api/category/list` | Enabled categories, sorted by `sort` | ✗ |
| GET  | `/api/city/list` | Enabled cities, sorted by `sort` (30 major cities seeded) | ✗ |

#### Shows

| Method | Path | Description | Auth |
|--------|------|-------------|:----:|
| POST | `/api/show/list` | Published show list (paginated; filter by name / categoryId / cityCode / venue; each item is a ShowVO with `categoryName` / `cityName` / `address` / `extend`) | ✗ |
| GET  | `/api/show/{id}` | Show detail (ShowVO with `categoryName` / `cityName` / `address` / `extend`) | ✗ |

#### Sessions

| Method | Path | Description | Auth |
|--------|------|-------------|:----:|
| POST | `/api/session/list` | Session list (paginated, filterable by status / startTime / endTime) | ✗ |
| POST | `/api/session/detail` | Session seat map (area prices + real-time availability + show / city / address info) | ✗ |

#### Orders

| Method | Path | Description | Auth |
|--------|------|-------------|:----:|
| POST | `/api/order/submit` | Lock seats + pre-gen orderNo + publish create-MQ, returns `{orderNo, "PROCESSING"}` immediately | ✓ |
| GET  | `/api/order/createStatus` | Poll async order creation status (PROCESSING/SUCCESS/FAILED/NOT_FOUND) | ✓ |
| POST | `/api/order/cancel` | Cancel order (unpaid: sync cancel; paid / partial-refund: initiate refund) | ✓ |
| GET  | `/api/order/orderDetails` | Order detail (owner only; includes showCityName / showAddress) | ✓ |
| POST | `/api/order/refundTicket` | Refund a single ticket (works on paid or partially-refunded orders) | ✓ |
| POST | `/api/order/list` | My orders (paginated, filterable by status / date range) | ✓ |

#### Payment & Verification

| Method | Path | Description | Auth |
|--------|------|-------------|:----:|
| POST | `/api/payment/create` | Pay order | ✓ |
| POST | `/api/verify/qr` | Verify by QR code | ✗ |
| POST | `/api/verify/ticket` | Verify by ticket number | ✗ |

---

### Admin Service (:8081)

#### Show Categories

Dedicated `category` table; shows link via `categoryId`. Deleting a category that's still referenced returns `1012 CATEGORY_IN_USE`.

| Method | Path | Description |
|--------|------|-------------|
| GET    | `/api/admin/category/list?status=&keyword=` | List categories (optional status / keyword prefix filter) |
| POST   | `/api/admin/category/create` | Create category (`name` unique, duplicate returns 1013) |
| PUT    | `/api/admin/category/update` | Update category (`status=0` hides it from the user-side list) |
| DELETE | `/api/admin/category/{id}` | Delete (returns 1012 when referenced by any show) |

#### Cities (read-only)

Seeded by `schema.sql` with 30 major cities; no admin write endpoint.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/admin/city/list?status=&keyword=` | Source for the city dropdown when creating a show |

#### Venue Templates (Room Management)

Define the seat layout and default prices on a room once; specifying `roomId` when creating a session automatically copies all seats and price areas.

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/admin/room/create` | Create room |
| PUT  | `/api/admin/room/update` | Update room |
| GET  | `/api/admin/room/{id}` | Room detail |
| GET  | `/api/admin/room/list` | Room list |
| POST | `/api/admin/room/seat/batch` | Save room seat template (overwrite) |
| GET  | `/api/admin/room/seat/list?roomId=` | Room seat template list |
| POST | `/api/admin/room/area/save` | Save room default price areas (overwrite) |
| GET  | `/api/admin/room/area/list?roomId=` | Room default price area list |
| GET  | `/api/admin/room/template?roomId=` | **Aggregate query**: returns room + seats + areas in one call, ready for seat-map rendering |

#### File Upload

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/admin/upload/image` | Upload an image to MinIO (`multipart/form-data`, field `file`, optional `dir` default `misc`); returns the externally accessible URL |

#### Shows & Sessions

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/admin/show/create` | Create show |
| PUT  | `/api/admin/show/update` | Update show |
| GET  | `/api/admin/show/{id}` | Show detail |
| GET  | `/api/admin/show/list` | Show list |
| POST | `/api/admin/session/create` | Create session (pass `roomId` to auto-copy seats + prices) |
| PUT  | `/api/admin/session/update` | Update session |
| GET  | `/api/admin/session/{id}` | Session detail |
| GET  | `/api/admin/session/list?showId=` | Session list |
| PUT  | `/api/admin/session/{sessionId}/publish` | Publish session for sale |

#### Seats (Manual — use when no room template)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/admin/seat/batch` | Batch create seats |
| GET  | `/api/admin/seat/list?sessionId=` | Seat list |
| POST | `/api/admin/seat/area/save` | Save session price areas |
| GET  | `/api/admin/seat/area/list?sessionId=` | Session price area list |
| POST | `/api/admin/seat/warmup/{sessionId}` | Warm up seat inventory into Redis |

#### Orders & Monitor

| Method | Path | Description |
|--------|------|-------------|
| GET  | `/api/admin/order/{id}` | Order detail (raw Order entity) |
| GET  | `/api/admin/order/query?orderNo=` | Lookup by order number |
| GET  | `/api/admin/order/{id}/items` | Order line items (with seat info) |
| POST | `/api/admin/order/list` | **Paginated order list** with filters: showId / sessionId / orderNo / status / time range; same response shape as the user-side list (includes show / city / tickets) |
| GET  | `/api/admin/monitor/dashboard?sessionId=` | Real-time seat counts (total / available / sold) |

</details>

---

## Core Booking Flow (Async Order Creation)

```
User submits seat selection — POST /api/order/submit
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

---

## Refund Flow

```
Order status: 1 (PAID) or 5 (PARTIAL_REFUND)
    │
    ├─ Full cancel  POST /api/order/cancel
    │       └─ Find all unused tickets → doRefund → status → REFUNDING (3)
    │
    └─ Per-ticket   POST /api/order/refundTicket
            └─ Validate ticket is unused → doRefund → status → REFUNDING (3)
                        │
              MQ consumer processes refund result
                        │
            ┌───────────┴───────────┐
            │                       │
    Unused tickets remain       All tickets refunded
    status → PARTIAL_REFUND (5) status → REFUNDED (4)
```

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
Async Order Creation (Direct):
  order.create.exchange ──→ order.create.queue ──→ OrderCreateConsumer (INSERT order + items + send timeout MQ)
                              (3-retry built-in; failure → marks Redis pending key as FAILED)

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
| `seat` | Seat master table; real-time inventory lives in Redis, `status` synced async after payment |
| `seat_area` | Per-session seat price areas |
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
| `session:seats:{sessionId}` | Set | Available seat ID pool | 7 days |
| `seat:info:{seatId}` | Hash | Seat details: row / col / type / area | 7 days |
| `seat:lock:{sessionId}:{seatId}` | String | Seat lock (value = userId) | 5 min |
| `session:purchase:{sessionId}:{userId}` | String | Per-user purchase count | 7 days |
| `session:area:price:{sessionId}:{areaId}` | Hash | Area price cache | 7 days |
| `session:locked:{sessionId}` | String | Count of seats currently locked in checkout | 7 days |
| `rate:global:{method}:{window}` | String | Global rate-limit counter | dynamic |
| `rate:user:{userId}:{method}:{window}` | String | User rate-limit counter | dynamic |
| `rate:ip:{ip}:{method}:{window}` | String | IP rate-limit counter | dynamic |
| `blacklist:user:{userId}` | String | User blacklist | custom |
| `blacklist:ip:{ip}` | String | IP blacklist | custom |

---

## Concurrency Design

| Problem | Solution |
|---------|----------|
| Oversell | Redis Set `SREM` atomic deduction + DB safety check |
| Purchase limit | Lua atomic INCR + threshold check |
| Traffic spike | `@RateLimit` annotation limiting: global / user / IP three dimensions |
| Batch seat lock | Lua script — full rollback on any failure, no partial locks |
| Order timeout | RabbitMQ TTL + dead-letter queue, exactly 5 minutes |
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
