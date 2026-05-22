# 部署说明

## 必须设置的环境变量

部署到任何非本地环境（包括测试、预发、生产）前，**必须**设置以下环境变量。若缺失，对应服务启动会直接失败（fail-fast）。

| 环境变量 | 用途 | 取值要求 | 涉及服务 |
|----------|------|---------|----------|
| `JWT_SECRET` | JWT 签名密钥 | 至少 32 字符强随机串（≥256 bit） | user |
| `ADMIN_API_KEY` | 管理后台 API Key | 至少 16 字符强随机串 | admin |
| `SNOWFLAKE_WORKER_ID` | Snowflake 节点编号 | 每个 Pod / 副本唯一（0-31 整数）；**prod 配置已去掉默认值，未注入时启动失败** | user / admin / payment |
| `DB_PASS` | MySQL 密码 | 强密码 | user / admin / payment |
| `REDIS_PASSWORD` | Redis 密码 | 强密码 | user / admin / payment |

### 可选环境变量

| 环境变量 | 用途 | 默认值 |
|----------|------|--------|
| `JWT_EXPIRE_MS` | JWT 过期时间（毫秒） | `7200000`（2 小时） |
| `DB_HOST` | MySQL 主机 | `localhost` |
| `DB_USER` | MySQL 用户名 | `root` |
| `REDIS_HOST` | Redis 主机 | `localhost` |
| `REDIS_PORT` | Redis 端口 | `6379` |

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

# 生成 ADMIN_API_KEY（24 字节 base64，约 32 字符）
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
      ADMIN_API_KEY: ${ADMIN_API_KEY}
      SNOWFLAKE_WORKER_ID: 2
      DB_HOST: mysql
      DB_PASS: ${DB_PASS}
      REDIS_HOST: redis
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      SPRING_PROFILES_ACTIVE: prod
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
  ADMIN_API_KEY: "<openssl rand -base64 24>"
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

## 调用管理后台示例

所有 `/api/admin/**` 请求必须携带 `X-Admin-Token` 头：

```bash
curl -H "X-Admin-Token: $ADMIN_API_KEY" \
     http://admin.example.com/api/admin/monitor/dashboard?sessionId=1
```

未带或错误的 token 返回 `401 Unauthorized`。

## 安全检查清单

部署前确认：

- [ ] `JWT_SECRET` 已通过 `openssl rand` 生成，未使用 dev 默认值
- [ ] `ADMIN_API_KEY` 已生成，未使用 `dev-admin-token-please-change-in-prod`
- [ ] 每个 Pod / 副本的 `SNOWFLAKE_WORKER_ID` 唯一（StatefulSet ordinal 或显式配置）
- [ ] MySQL / Redis / RabbitMQ 不使用默认密码（`root123` / `guest`）
- [ ] 反向代理后部署时已配置 `rate-limit.trusted-proxies`
- [ ] dev 配置文件（`application-dev.yml`）未被打包到生产镜像（或 `SPRING_PROFILES_ACTIVE=prod` 已设置）
- [ ] RabbitMQ 管理端口（15672）未对公网暴露
