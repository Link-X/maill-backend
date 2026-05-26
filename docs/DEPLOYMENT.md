# 部署说明

## 必须设置的环境变量

部署到任何非本地环境（包括测试、预发、生产）前，**必须**设置以下环境变量。若缺失，对应服务启动会直接失败（fail-fast）。

| 环境变量 | 用途 | 取值要求 | 涉及服务 |
|----------|------|---------|----------|
| `JWT_SECRET` | JWT 签名密钥 | 至少 32 字符强随机串（≥256 bit） | user / admin |
| `ADMIN_INVITE_CODE` | 管理员注册邀请码 | 至少 16 字符强随机串；仅运维持有 | admin |
| `SNOWFLAKE_WORKER_ID` | Snowflake 节点编号 | 每个 Pod / 副本唯一（0-31 整数）；**prod 配置已去掉默认值，未注入时启动失败** | user / admin / payment |
| `DB_PASS` | MySQL 密码 | 强密码 | user / admin / payment |
| `REDIS_PASSWORD` | Redis 密码 | 强密码 | user / admin / payment |
| `MINIO_ENDPOINT` | MinIO/S3 服务端点 | 如 `https://s3.your-domain.com`；admin 模块若启用图片上传则必填 | admin |
| `MINIO_ACCESS_KEY` | MinIO/S3 访问 Key | 强随机串 | admin |
| `MINIO_SECRET_KEY` | MinIO/S3 访问 Secret | 强随机串 | admin |
| `MINIO_BUCKET` | 存储 bucket 名称 | 如 `ticket-prod` | admin |
| `MINIO_PUBLIC_ENDPOINT` | 浏览器访问图片用的对外 host | CDN / 反向代理域名；未配置则回退到 `MINIO_ENDPOINT` | admin |

### 可选环境变量

| 环境变量 | 用途 | 默认值 |
|----------|------|--------|
| `JWT_EXPIRE_MS` | JWT 过期时间（毫秒） | `7200000`（2 小时） |
| `DB_HOST` | MySQL 主机 | `localhost` |
| `DB_USER` | MySQL 用户名 | `root` |
| `REDIS_HOST` | Redis 主机 | `localhost` |
| `REDIS_PORT` | Redis 端口 | `6379` |
| `ELASTICSEARCH_HOST` | Elasticsearch 节点（多节点逗号分隔，如 `es-1:9200,es-2:9200`） | `localhost:9200` |
| `ELASTICSEARCH_CONNECT_TIMEOUT_MS` | 连接超时（毫秒） | `3000` |
| `ELASTICSEARCH_SOCKET_TIMEOUT_MS` | 读超时（毫秒） | `10000` |

> **Elasticsearch 说明**：演出 / 艺人 / 资讯三类索引由各服务启动时的 `IndexInitializer` 幂等创建（已存在则跳过）；ES 不可用时业务降级，不会阻塞应用启动，但搜索功能与异步索引会失败。生产环境建议：(1) 至少 3 节点集群 + 副本数 ≥ 1；(2) 开启 X-Pack security 并通过环境变量注入用户名密码（当前代码用匿名访问，启用 security 后需扩展 `ElasticsearchClientConfig`）；(3) 与 MySQL 同机房部署降低 MQ 同步延迟。

> **MinIO / 对象存储说明**：admin 模块的图片上传走 S3 兼容协议，本地开发使用 `docker-compose.yml` 内置的 MinIO 容器（9000 API / 9001 控制台，默认 `minioadmin / minioadmin123`）。生产环境可继续使用自建 MinIO，也可直接切到阿里云 OSS / AWS S3 —— 只需替换上面 5 个环境变量，业务代码不动。bucket 不存在时 admin 首次启动会自动创建并设置公共读策略，**生产环境如不希望对外公开访问，应改用签名 URL 并移除公共读策略**。

### 部署在反向代理后

如服务部署在 Nginx / ELB / Ingress 等反向代理之后，需要从 `X-Forwarded-For` 取真实客户端 IP 用于限流，则必须显式配置可信代理 IP 白名单（防止外网客户端伪造 IP 绕过限流）：

```yaml
# application-prod.yml 或通过启动参数 --rate-limit.trusted-proxies=...
rate-limit:
  trusted-proxies: 10.0.0.1,10.0.0.2
```

未配置时默认不信任任何代理头，所有限流以 `RemoteAddr` 为准。

## 生成强随机密钥

```bash
# 生成 JWT_SECRET（32 字节 base64，约 44 字符）
openssl rand -base64 32

# 生成 ADMIN_INVITE_CODE（24 字节 base64，约 32 字符）
openssl rand -base64 24
```

## 启动示例

### Docker Compose

```yaml
services:
  user:
    image: ticket/user:latest
    environment:
      JWT_SECRET: ${JWT_SECRET}
      SNOWFLAKE_WORKER_ID: 1
      DB_HOST: mysql
      DB_PASS: ${DB_PASS}
      REDIS_HOST: redis
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      SPRING_PROFILES_ACTIVE: prod

  admin:
    image: ticket/admin:latest
    environment:
      JWT_SECRET: ${JWT_SECRET}                  # 与 user 共享同一签发体系
      ADMIN_INVITE_CODE: ${ADMIN_INVITE_CODE}
      SNOWFLAKE_WORKER_ID: 2
      DB_HOST: mysql
      DB_PASS: ${DB_PASS}
      REDIS_HOST: redis
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      MINIO_ENDPOINT: ${MINIO_ENDPOINT}          # 如 https://s3.your-domain.com
      MINIO_ACCESS_KEY: ${MINIO_ACCESS_KEY}
      MINIO_SECRET_KEY: ${MINIO_SECRET_KEY}
      MINIO_BUCKET: ${MINIO_BUCKET}              # 如 ticket-prod
      MINIO_PUBLIC_ENDPOINT: ${MINIO_PUBLIC_ENDPOINT}  # CDN / 反向代理域名
      ELASTICSEARCH_HOST: ${ELASTICSEARCH_HOST}        # 如 es-1:9200,es-2:9200,es-3:9200
      SPRING_PROFILES_ACTIVE: prod

  user:
    image: ticket/user:latest
    environment:
      # 与 admin 共享 JWT_SECRET / DB / Redis / Snowflake
      ELASTICSEARCH_HOST: ${ELASTICSEARCH_HOST}        # 用户端 /api/search/* 依赖
      MINIO_ENDPOINT: ${MINIO_ENDPOINT}                # 用户端评价晒图上传
      MINIO_ACCESS_KEY: ${MINIO_ACCESS_KEY}
      MINIO_SECRET_KEY: ${MINIO_SECRET_KEY}
      MINIO_BUCKET: ${MINIO_BUCKET}
      MINIO_PUBLIC_ENDPOINT: ${MINIO_PUBLIC_ENDPOINT}
```

### Kubernetes

将密钥放入 Secret，通过 envFrom 注入：

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: ticket-secrets
type: Opaque
stringData:
  JWT_SECRET: "<openssl rand -base64 32>"
  ADMIN_INVITE_CODE: "<openssl rand -base64 24>"
  DB_PASS: "<db-password>"
  REDIS_PASSWORD: "<redis-password>"
---
apiVersion: apps/v1
kind: StatefulSet  # 用 StatefulSet 保证 Pod 序号稳定,SNOWFLAKE_WORKER_ID 可用 ordinal 索引派生
metadata:
  name: ticket-user
spec:
  serviceName: ticket-user
  replicas: 3
  template:
    spec:
      containers:
        - name: user
          envFrom:
            - secretRef:
                name: ticket-secrets
          env:
            - name: SNOWFLAKE_WORKER_ID
              valueFrom:
                fieldRef:
                  fieldPath: metadata.labels['apps.kubernetes.io/pod-index']
```

## 管理员注册 / 登录 / 调用

### 注册管理员账号

需要持有 `ADMIN_INVITE_CODE`：

```bash
curl -X POST http://admin.example.com/api/admin/auth/register \
     -H 'Content-Type: application/json' \
     -d '{
       "username": "ops_alice",
       "password": "<strong-password>",
       "inviteCode": "<ADMIN_INVITE_CODE>"
     }'
# 返回 { "token": "...", "userId": ..., "roles": ["ADMIN"] }
```

### 登录获取 JWT

```bash
curl -X POST http://admin.example.com/api/admin/auth/login \
     -H 'Content-Type: application/json' \
     -d '{ "username": "ops_alice", "password": "<strong-password>" }'
# 返回 { "token": "...", "userId": ..., "roles": ["ADMIN"] }
```

### 调用受保护接口

所有 `/api/admin/**`（除 `/api/admin/auth/**`）必须携带 Bearer JWT：

```bash
curl -H "Authorization: Bearer $ADMIN_TOKEN" \
     http://admin.example.com/api/admin/monitor/dashboard?sessionId=1
```

未登录返回 `401`，已登录但无 ADMIN 角色返回 `403`。

## 安全检查清单

部署前确认：

- [ ] `JWT_SECRET` 已通过 `openssl rand` 生成，未使用 dev 默认值
- [ ] `ADMIN_INVITE_CODE` 已生成，未使用 `dev-admin-invite-please-change-in-prod`
- [ ] 每个 Pod / 副本的 `SNOWFLAKE_WORKER_ID` 唯一（StatefulSet ordinal 或显式配置）
- [ ] MySQL / Redis / RabbitMQ 不使用默认密码（`root123` / `guest`）
- [ ] MinIO 不使用默认凭据（`minioadmin / minioadmin123`），且生产环境如不需要公开访问，已改为签名 URL 模式
- [ ] 反向代理后部署时已配置 `rate-limit.trusted-proxies`
- [ ] dev 配置文件（`application-dev.yml`）未被打包到生产镜像（或 `SPRING_PROFILES_ACTIVE=prod` 已设置）
- [ ] RabbitMQ 管理端口（15672）、MinIO 控制台端口（9001）、Elasticsearch HTTP 端口（9200）未对公网暴露
- [ ] Elasticsearch 已设置副本数 ≥ 1（dev 单节点的 yellow 状态不可用于生产），并开启 X-Pack security
- [ ] `SubscribeNotifier` 开售提醒定时任务在多副本部署时，建议加分布式锁或只在单实例启用（当前 `@Scheduled` 每个 Pod 都会触发，重复推送由 `notified_pre`/`notified_open` 幂等标记拦截）
