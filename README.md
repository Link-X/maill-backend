# Ticket Booking System — Backend

![Java](https://img.shields.io/badge/Java-11-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7.x-brightgreen)
![MySQL](https://img.shields.io/badge/MySQL-8.x-orange)
![Redis](https://img.shields.io/badge/Redis-7.x-red)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-3.x-ff6600)
![MinIO](https://img.shields.io/badge/MinIO-S3%20compatible-c72e49)
![License](https://img.shields.io/badge/license-MIT-lightgrey)

[中文文档](README.zh.md)

A high-concurrency ticket booking backend targeting thousands to tens of thousands of concurrent users. Features show management, seat selection, Redis-based inventory, order timeout cancellation, mock payment, venue check-in verification, and partial refunds. Built with a Maven multi-module architecture for independent deployment.

> **Companion frontend repository**: [Link-X/maill-frontend](https://github.com/Link-X/maill-frontend)

---

## Features

- **Show Management** — CRUD for shows / sessions / seats; admin warms up seat inventory into Redis with one click
- **Category Management** — Dedicated `category` table + admin CRUD; shows link via `categoryId`, list/detail endpoints LEFT JOIN to return `categoryName` in a single call; user-side `/api/category/list` powers the home-page tabs
- **City & Address** — `city` table seeded with 30 major cities (GB/T administrative codes); shows include `cityCode` + `address`; list / detail / order / session-detail endpoints all return `cityName`; user-side home page can switch by city
- **Extend Fields** — `show` / `show_session` expose an `extend JSON` column so product can add display-only attributes without ALTER TABLE; conventions live in the frontend doc (not used in WHERE / indexes)
- **Venue Templates** — Define seat layout and default prices once on a room; sessions created with a `roomId` auto-copy all seats and price areas instantly; `/room/template` endpoint returns room + seats + areas in a single call
- **Image Upload** — Admin `/upload/image` endpoint backed by MinIO object storage (S3-compatible) for show posters, venue maps, etc.; returns an externally accessible URL
- **Reporting** — Admin `/api/admin/report/*` exposes 11 aggregate endpoints (overview / time series / by show / category / city / status / hour / session fill-rate / user / refund / cancellation); rolling time windows (1d/7d/30d/90d) or custom; results cached in Redis for 5 minutes. The `order` table now tracks `refund_amount` cumulatively and a `cancel_reason` to distinguish user-cancelled vs. timeout-cancelled
- **Booking Core** — Lua atomic purchase-limit check + Redis batch seat lock (full rollback on any failure) + synchronous order creation
- **Oversell Prevention** — Redis Set atomic `SREM` deduction + DB-level safety check
- **Order Timeout** — RabbitMQ TTL + dead-letter queue, cancels order and releases inventory exactly 5 minutes after creation
- **Async Events** — After payment, RabbitMQ Fanout fan-out triggers ticket generation, DB inventory sync, and notification (reserved) in parallel
- **Refunds** — Full-order and per-ticket refunds; partially-refunded orders (status 5) can continue to refund remaining unused tickets
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
| Framework | Spring Boot | 2.7.x / JDK 11 |
| ORM | MyBatis | 3.5.x |
| Cache / Lock | Redis + Redisson | 7.x / 3.x |
| Message Queue | RabbitMQ | 3.x |
| Database | MySQL | 8.x |
| Object Storage | MinIO (S3-compatible) | 8.5.x SDK |
| Auth | Spring Security + JJWT | 0.12.x |
| Build | Maven | — |

---

## Module Structure

```
maill-backend/
├── common/      # Utilities: response wrapper, exceptions, Snowflake ID, RedisKeys, @RateLimit AOP, blacklist, TraceIdFilter
├── core/        # Core business: entities, Mappers, Services, MQ Producer/Consumer, MyBatis JsonMapTypeHandler
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
| `ShowService` | Show CRUD |
| `SessionService` | Session CRUD + publish + seat-map aggregate query |
| `CategoryService` / `CityService` | Category / city (city is read-only) |
| `RoomService` | Venue template + `copyToSession` (copies seats / price areas) |
| `OrderCommandService` | Place / cancel / refund (transactional writes) |
| `OrderQueryService` | Single / list queries + OrderStatusResponse assembly (batch prefetch, avoids N+1) |
| `SeatInventoryService` / `PurchaseLimitService` | Redis inventory and purchase limits |
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

- JDK 11+
- Maven 3.8+
- Docker & Docker Compose

### 1. Start infrastructure

```bash
docker-compose up -d
```

Starts MySQL 8 (3306), Redis 7 (6379), RabbitMQ 3 (5672, management UI on 15672), and MinIO (9000 API / 9001 console). `sql/schema.sql` is executed automatically on first run; the MinIO `image` bucket (configured via `minio.bucket` in `application-dev.yml`) is auto-created with a public-read policy the first time admin starts — no manual setup required.

> **RabbitMQ Management UI**: http://localhost:15672 (guest / guest)
> **MinIO Console**: http://localhost:9001 (minioadmin / minioadmin123)

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
| Shows | `/api/show/*` | List (name/categoryId/cityCode/venue filters) / detail; returns ShowVO with categoryName / cityName / address / extend |
| Sessions | `/api/session/*` | List / seat-map detail (includes show + city + address) |
| Orders | `/api/order/*` | Lock & create / cancel / single-ticket refund / my orders / detail |
| Payment | `/api/payment/*` | Pay an order |
| Verification | `/api/verify/*` | QR / ticket-number check-in |

### Admin Service (:8081)

| Module | Path prefix | Main capabilities |
|--------|------------|-------------------|
| Auth | `/api/admin/auth/*` | Admin register / login (requires `ADMIN_INVITE_CODE`) |
| Categories | `/api/admin/category/*` | CRUD; deleting a referenced category returns 1012 |
| Cities (read-only) | `/api/admin/city/list` | Dropdown source for the show form; seeded by schema.sql |
| Venue templates | `/api/admin/room/*` | Room CRUD + seat template + default price areas + `/template` aggregate |
| Image upload | `/api/admin/upload/image` | Multipart upload to MinIO, returns external URL |
| Shows | `/api/admin/show/*` | Show CRUD (Request DTOs strictly validate; `status` not accepted from clients) |
| Sessions | `/api/admin/session/*` | Session CRUD + `/{id}/publish`; passing `roomId` auto-copies seats + prices |
| Seats (manual) | `/api/admin/seat/*` | Batch seats / price areas / Redis warmup when not using a room template |
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
| POST | `/api/order/submit` | Lock seats + create order, returns full order immediately | ✓ |
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

## Core Booking Flow

```
User submits seat selection
    │
    ├─ @RateLimit blacklist / IP / user / global check (AOP, first to intercept)
    ├─ Session validation
    ├─ Lua atomic purchase-limit check (Redis)
    ├─ Lua batch seat lock (full rollback on any failure)
    ├─ Synchronous order creation (DB INSERT)
    ├─ Send timeout message to RabbitMQ (TTL = 5 min)
    └─ Return full order info (show / session / seats / total / countdown)
                │
    ┌───────────┴───────────┐
    │                       │
User clicks Pay on       No payment within 5 min
  confirmation page          │
    │                   Timeout message routed via DLX
POST /api/payment/create to order.cancel.queue
    │                       │
    ├─ Create payment record Cancel order
    ├─ Order status → PAID  Release Redis inventory
    └─ Send payment event   Roll back purchase count
              │
    ┌─────────┼──────────┐
    │         │          │
Generate   Sync DB    Send notification
Tickets   inventory   (reserved)
(async)    (async)
```

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
Order Timeout (TTL + Dead Letter):
  order.timeout.exchange ──→ order.timeout.queue (TTL 5 min)
                                      │ expires
  order.dead.exchange    ──→ order.cancel.queue ──→ OrderTimeoutConsumer (cancel order)

Payment Success (Fanout):
  payment.success.exchange ──→ ticket.generate.queue  ──→ generate tickets
                           ──→ inventory.sync.queue   ──→ sync seat.status = SOLD
                           ──→ notification.queue     ──→ notify (reserved)
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

15 tables in total:

| Table | Description |
|-------|-------------|
| `user` | Users, BCrypt passwords |
| `user_role` | Roles: USER / ADMIN |
| `category` | Show categories (`name` unique; `sort`/`status` for ordering and enable/disable; `idx_status_sort` index) |
| `city` | Cities (GB/T administrative codes, `code` unique); 30 major cities seeded, no write endpoint |
| `show` | Shows; `category_id` / `city_code` link to category and city; `address` for the full street address; `extend` JSON for ad-hoc display fields; `idx_name` / `idx_venue` / `idx_category_id` / `idx_city_code` for search and filtering |
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
            ┌──────────────────┼──────────────────┐
            │                  │                  │
   ┌────────▼────────┐ ┌───────▼──────┐ ┌────────▼────────┐
   │    MySQL 8      │ │   Redis 7    │ │   RabbitMQ 3    │
   │  (replication   │ │ (cache/lock) │ │ (events/timeout)│
   │   optional)     │ │              │ │                 │
   └─────────────────┘ └──────────────┘ └─────────────────┘
```

---

## Roadmap

- **Real payment gateways** — implement `PaymentGateway` for Alipay / WeChat Pay
- **Notification service** — integrate SMS / push, implement `notification.queue` consumer
- **Microservices** — split admin / user / payment into independent services behind an API Gateway
- **Sharding** — partition the order table by `session_id`
- **CDN** — offload show poster and static assets; swap MinIO for Aliyun OSS / AWS S3 in production by changing `minio.endpoint` / `accessKey` only

---

## License

[MIT](LICENSE)
