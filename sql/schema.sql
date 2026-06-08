-- 抢票系统数据库建表脚本
-- MySQL 8.0+

CREATE DATABASE IF NOT EXISTS ticket_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE ticket_system;

-- 1. 用户表
-- `user` 是 MySQL 保留字，必须使用反引号
CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT NOT NULL COMMENT '用户ID(雪花ID)',
    username VARCHAR(64) NOT NULL COMMENT '用户名',
    phone VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    email VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    password_hash VARCHAR(128) NOT NULL COMMENT '密码哈希(BCrypt)',
    status INT NOT NULL DEFAULT 1 COMMENT '状态: 0=禁用, 1=正常',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 2. 用户角色表
CREATE TABLE IF NOT EXISTS user_role (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role VARCHAR(32) NOT NULL COMMENT '角色: ADMIN, USER',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色表';

-- 3. 演出分类表
CREATE TABLE IF NOT EXISTS category (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64)  NOT NULL COMMENT '分类名',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序，小靠前',
    icon        VARCHAR(255)          DEFAULT NULL COMMENT '可选图标 URL',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name),
    KEY idx_status_sort (status, sort) COMMENT '用户端列表按 status=1 过滤后按 sort 排序'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演出分类表';

-- 3.5 城市表（GB/T 行政区划代码，仅一级行政区与主要地级市；只读 seed，不提供后台 CRUD）
CREATE TABLE IF NOT EXISTS city (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    code        VARCHAR(10)  NOT NULL COMMENT 'GB/T 行政区划代码',
    name        VARCHAR(64)  NOT NULL COMMENT '城市名',
    sort        INT          NOT NULL DEFAULT 0 COMMENT '排序，小靠前',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '0=禁用 1=启用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    KEY idx_status_sort (status, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='城市表';

-- 4. 演出表
-- `show` 是 MySQL 保留字，必须使用反引号
-- 索引设计:
--   idx_status_create_time : 按状态分页主用
--   idx_name / idx_venue   : 前缀模糊搜索走 B-Tree (LIKE 'xxx%')
--   idx_category_id        : 按分类筛选（前端分类 tabs）
CREATE TABLE IF NOT EXISTS `show` (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    name           VARCHAR(128)  NOT NULL COMMENT '演出名称',
    description    TEXT                   COMMENT '演出描述',
    category_id    BIGINT                 DEFAULT NULL COMMENT '关联 category.id',
    city_code      VARCHAR(10)            DEFAULT NULL COMMENT '关联 city.code (GB/T 行政区划代码)',
    address        VARCHAR(255)           DEFAULT NULL COMMENT '详细地址',
    poster_url     VARCHAR(512)           DEFAULT NULL COMMENT '海报URL',
    venue          VARCHAR(256)           DEFAULT NULL COMMENT '演出场馆名',
    status         INT           NOT NULL DEFAULT 0 COMMENT '状态: 0=草稿, 1=已上架, 2=已下架',
    review_mode    TINYINT       NOT NULL DEFAULT 1 COMMENT '评价模式 0=无评价 1=所有可评 2=仅已观看',
    avg_rating     DECIMAL(3, 2) NOT NULL DEFAULT 0 COMMENT '平均评分,冗余统计',
    review_count   INT           NOT NULL DEFAULT 0 COMMENT '评价数,冗余统计',
    extend         JSON                   DEFAULT NULL COMMENT '扩展字段（如预售/退改规则/时长/适宜年龄），约定见前端文档；不参与 WHERE/索引',
    create_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_status_create_time (status, create_time) COMMENT '用于演出列表按状态分页查询，避免 filesort',
    KEY idx_name           (name)           COMMENT '搜索: name LIKE xxx%',
    KEY idx_venue          (venue)          COMMENT '搜索: venue LIKE xxx%',
    KEY idx_category_id    (category_id)    COMMENT '按分类筛选',
    KEY idx_city_code      (city_code)      COMMENT '按城市筛选'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演出表';

-- 4. 演出场次表
CREATE TABLE IF NOT EXISTS show_session (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    show_id        BIGINT       NOT NULL COMMENT '关联演出ID',
    room_id        BIGINT       DEFAULT NULL COMMENT '关联场地ID，不为空时座位由场地模板复制',
    name           VARCHAR(128) NOT NULL COMMENT '场次名称',
    start_time     DATETIME     NOT NULL COMMENT '开始时间',
    end_time       DATETIME     NOT NULL COMMENT '结束时间',
    total_seats    INT          NOT NULL DEFAULT 0 COMMENT '总座位数',
    limit_per_user INT          NOT NULL DEFAULT 1 COMMENT '每用户限购数量',
    status         INT          NOT NULL DEFAULT 0 COMMENT '状态: 0=未开放, 1=销售中, 2=已结束',
    row_count      INT          NOT NULL DEFAULT 0 COMMENT '座位网格行数',
    col_count      INT          NOT NULL DEFAULT 0 COMMENT '座位网格列数',
    open_sale_time DATETIME              DEFAULT NULL COMMENT '开售时间;NULL 表示无需等待,定时任务可立即流转为销售中',
    extend         JSON                  DEFAULT NULL COMMENT '扩展字段（如开售提前N分钟/特殊提示），不参与 WHERE/索引',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_show_id (show_id),
    KEY idx_status_open_sale (status, open_sale_time),
    KEY idx_status_end_time (status, end_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演出场次表';

-- 5. 座位表
CREATE TABLE IF NOT EXISTS seat (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    session_id   BIGINT      NOT NULL COMMENT '关联场次ID',
    row_no       INT         NOT NULL COMMENT '排号',
    col_no       INT         NOT NULL COMMENT '列号',
    type         INT         NOT NULL DEFAULT 1  COMMENT '座位类型: 1=普通, 2=情侣左, 3=情侣右',
    area_id      VARCHAR(32) NOT NULL DEFAULT '' COMMENT '价格区域ID，对应 seat_area.area_id',
    seat_name    VARCHAR(64)          DEFAULT NULL COMMENT '座位名称，如 1排01座',
    pair_seat_id BIGINT               DEFAULT NULL COMMENT '情侣连座配对座位ID，type=2/3时非空',
    status       INT         NOT NULL DEFAULT 0  COMMENT '状态: 0=可售, 1=已锁定, 2=已售',
    create_time  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_session_id (session_id),
    KEY idx_session_status (session_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='座位表';

-- 6. 订单表
-- `order` 是 MySQL 保留字，必须使用反引号
CREATE TABLE IF NOT EXISTS `order` (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(32) NOT NULL COMMENT '订单编号(雪花ID)',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    session_id BIGINT NOT NULL COMMENT '场次ID',
    total_amount DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
    status INT NOT NULL DEFAULT 0 COMMENT '状态: 0=待支付, 1=已支付, 2=已取消, 3=退款中, 4=已退款, 5=部分退款',
    refund_amount DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '已退款金额累计（部分退款会多次累加）',
    cancel_reason TINYINT DEFAULT NULL COMMENT '取消原因: 0=用户主动, 1=超时自动; status=2 时才有值',
    pay_time DATETIME DEFAULT NULL COMMENT '支付时间',
    expire_time DATETIME NOT NULL COMMENT '过期时间(下单后5分钟)',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_user_id (user_id),
    KEY idx_user_status (user_id, status) COMMENT '用于用户订单列表查询',
    KEY idx_session_id (session_id) COMMENT '用于按场次查询订单',
    KEY idx_status_expire (status, expire_time) COMMENT '用于超时订单扫描',
    KEY idx_create_time (create_time) COMMENT '用于报表按时间窗口扫描'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- 7. 订单明细表
CREATE TABLE IF NOT EXISTS order_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL COMMENT '关联订单ID',
    seat_id BIGINT NOT NULL COMMENT '座位ID',
    price DECIMAL(10,2) NOT NULL COMMENT '成交价格',
    seat_info VARCHAR(128) DEFAULT NULL COMMENT '座位信息(如"A排5座")',
    PRIMARY KEY (id),
    KEY idx_order_id (order_id),
    KEY idx_seat_id (seat_id),
    KEY idx_order_seat (order_id, seat_id) COMMENT '用于订单明细关联查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- 8. 支付表
CREATE TABLE IF NOT EXISTS payment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL COMMENT '关联订单ID',
    payment_no VARCHAR(64) NOT NULL COMMENT '支付流水号',
    channel VARCHAR(32) DEFAULT 'MOCK' COMMENT '支付渠道: MOCK, ALIPAY, WECHAT',
    amount DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    status INT NOT NULL DEFAULT 0 COMMENT '状态: 0=处理中, 1=成功, 2=失败',
    trade_no VARCHAR(128) DEFAULT NULL COMMENT '第三方交易号',
    callback_time DATETIME DEFAULT NULL COMMENT '回调时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payment_no (payment_no),
    KEY idx_order_id (order_id),
    KEY idx_order_status (order_id, status) COMMENT '用于按订单查询支付状态'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付表';

-- 9. 票据表
CREATE TABLE IF NOT EXISTS ticket (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL COMMENT '关联订单ID',
    seat_id BIGINT NOT NULL COMMENT '关联座位ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    qr_code VARCHAR(64) NOT NULL COMMENT '二维码内容(UUID)',
    ticket_no VARCHAR(16) NOT NULL COMMENT '票号(TK+6位随机)',
    status INT NOT NULL DEFAULT 0 COMMENT '状态: 0=未使用, 1=已使用, 2=已退款/作废',
    verify_time DATETIME DEFAULT NULL COMMENT '核验时间',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_qr_code (qr_code),
    UNIQUE KEY uk_ticket_no (ticket_no),
    KEY idx_order_id (order_id),
    KEY idx_seat_id (seat_id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='票据表';

-- 10. 座位价格区域表
-- sale_mode 决定该区域的售卖方式;同场次内允许混合模式(例如 VIP 区选座、看台区派座)
CREATE TABLE IF NOT EXISTS seat_area (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    session_id        BIGINT        NOT NULL COMMENT '关联场次ID',
    area_id           VARCHAR(32)   NOT NULL COMMENT '区域标识(如 0、1，场次内唯一)',
    price             DECIMAL(10,2) NOT NULL COMMENT '区域售价',
    origin_price      DECIMAL(10,2) NOT NULL COMMENT '区域原价',
    sale_mode         TINYINT       NOT NULL DEFAULT 1 COMMENT '售卖模式: 1=用户选座, 2=系统派座',
    single_total      INT           NOT NULL DEFAULT 0 COMMENT '区域内单座总数(type=1),初始化时按 seat 表统计',
    couple_total      INT           NOT NULL DEFAULT 0 COMMENT '区域内情侣对总数(type=2+3 配对),初始化时按 seat 表统计',
    allocate_strategy TINYINT       NOT NULL DEFAULT 1 COMMENT '派座策略(sale_mode=2 生效): 1=连坐优先, 2=分散, 3=任意',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_area (session_id, area_id),
    KEY idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='座位价格区域表(含售卖模式)';

-- 10.5 派座任务表（sale_mode=2 时的中间状态持久化）
-- 派座流程是「同步扣 Redis 库存 → 异步派具体座位 → 建单」,中间状态必须落库,
-- 否则进程崩溃会出现"库存扣了但订单没建"的脏数据。定时任务扫描超时回滚。
CREATE TABLE IF NOT EXISTS seat_allocation_task (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    order_no        VARCHAR(32)  NOT NULL COMMENT '订单号(submit 同步预生成)',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    session_id      BIGINT       NOT NULL COMMENT '场次ID',
    area_id         VARCHAR(32)  NOT NULL COMMENT '区域ID',
    ticket_type     TINYINT      NOT NULL COMMENT '票种: 1=单座, 2=情侣对',
    quantity        INT          NOT NULL COMMENT 'ticket_type=1 时为张数, =2 时为对数',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0=待派, 1=派座+建单成功, 2=失败, 3=已回滚',
    allocated_seats VARCHAR(1024) DEFAULT NULL COMMENT '逗号分隔的派出座位 ID(派座成功后写入)',
    fail_reason     VARCHAR(256)  DEFAULT NULL COMMENT '失败原因',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_status_create (status, create_time) COMMENT '定时回滚扫描用'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='派座任务表';

-- 11. 场地表（座位布局模板载体）
CREATE TABLE IF NOT EXISTS room (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(128) NOT NULL COMMENT '场地名称，如「主场地」「小剧场」',
    venue       VARCHAR(256) NOT NULL COMMENT '所属场馆名称',
    row_count   INT          NOT NULL DEFAULT 0 COMMENT '座位网格行数',
    col_count   INT          NOT NULL DEFAULT 0 COMMENT '座位网格列数',
    description VARCHAR(512) DEFAULT NULL COMMENT '备注',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场地表';

-- 12. 场地座位模板表
CREATE TABLE IF NOT EXISTS room_seat (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    room_id      BIGINT      NOT NULL COMMENT '关联场地ID',
    row_no       INT         NOT NULL COMMENT '排号',
    col_no       INT         NOT NULL COMMENT '列号',
    type         INT         NOT NULL DEFAULT 1 COMMENT '座位类型: 1=普通, 2=情侣左, 3=情侣右',
    area_id      VARCHAR(32) NOT NULL DEFAULT '' COMMENT '默认价格区域ID',
    seat_name    VARCHAR(64) DEFAULT NULL COMMENT '座位名称，如 1排01座',
    pair_seat_id BIGINT      DEFAULT NULL COMMENT '情侣连座配对座位ID（room_seat.id）',
    PRIMARY KEY (id),
    KEY idx_room_id (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场地座位模板表';

-- 13. 场地默认价格区域表
CREATE TABLE IF NOT EXISTS room_area (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    room_id           BIGINT       NOT NULL COMMENT '关联场地ID',
    area_id           VARCHAR(32)  NOT NULL COMMENT '区域标识，与 room_seat.area_id 对应',
    default_price     DECIMAL(10,2) NOT NULL COMMENT '默认售价',
    default_origin_price DECIMAL(10,2) NOT NULL COMMENT '默认原价',
    PRIMARY KEY (id),
    UNIQUE KEY uk_room_area (room_id, area_id),
    KEY idx_room_id (room_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='场地默认价格区域表';

-- ============================================================
-- 初始化数据：城市 seed（30 个主要城市，GB/T 行政区划代码）
-- 直辖市使用一级代码（如 110000），地级市使用二级代码（如 440100 广州）。
-- 上线后只读，不通过后台 CRUD 修改。
-- ============================================================
INSERT IGNORE INTO city (code, name, sort, status) VALUES
    ('110000', '北京',     1,  1),
    ('310000', '上海',     2,  1),
    ('440100', '广州',     3,  1),
    ('440300', '深圳',     4,  1),
    ('330100', '杭州',     5,  1),
    ('320100', '南京',     6,  1),
    ('510100', '成都',     7,  1),
    ('420100', '武汉',     8,  1),
    ('610100', '西安',     9,  1),
    ('120000', '天津',     10, 1),
    ('500000', '重庆',     11, 1),
    ('320500', '苏州',     12, 1),
    ('430100', '长沙',     13, 1),
    ('370200', '青岛',     14, 1),
    ('410100', '郑州',     15, 1),
    ('210200', '大连',     16, 1),
    ('330200', '宁波',     17, 1),
    ('350200', '厦门',     18, 1),
    ('350100', '福州',     19, 1),
    ('370100', '济南',     20, 1),
    ('340100', '合肥',     21, 1),
    ('320200', '无锡',     22, 1),
    ('440600', '佛山',     23, 1),
    ('441900', '东莞',     24, 1),
    ('530100', '昆明',     25, 1),
    ('210100', '沈阳',     26, 1),
    ('230100', '哈尔滨',   27, 1),
    ('220100', '长春',     28, 1),
    ('360100', '南昌',     29, 1),
    ('520100', '贵阳',     30, 1);

-- ============================================================
-- 用户端功能扩展表（艺人 / 资讯 / 收藏 / 订阅 / 消息 / 评价 / Banner）
-- 关联设计文档：docs/superpowers/specs/2026-05-25-user-features-expansion-design.md
-- ============================================================

-- ------------------------------------------------------------
-- 收藏分组表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS favorite_group (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL COMMENT '用户ID',
    name        VARCHAR(50) NOT NULL COMMENT '分组名',
    sort        INT         NOT NULL DEFAULT 0 COMMENT '排序,小靠前',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_name (user_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏分组';

-- ------------------------------------------------------------
-- 用户收藏演出表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_favorite (
    id          BIGINT     NOT NULL AUTO_INCREMENT,
    user_id     BIGINT     NOT NULL COMMENT '用户ID',
    show_id     BIGINT     NOT NULL COMMENT '演出ID',
    group_id    BIGINT     NULL     COMMENT '分组ID,NULL=未分组',
    create_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_show (user_id, show_id),
    KEY idx_user_group (user_id, group_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户收藏演出';

-- ------------------------------------------------------------
-- 开售提醒订阅表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS show_subscribe (
    id                    BIGINT   NOT NULL AUTO_INCREMENT,
    user_id               BIGINT   NOT NULL COMMENT '用户ID',
    show_id               BIGINT   NOT NULL COMMENT '演出ID',
    notify_before_minutes INT      NOT NULL DEFAULT 10 COMMENT '提前提醒分钟数',
    notified_pre          TINYINT  NOT NULL DEFAULT 0 COMMENT '历史字段,已弃用(按场次跟踪在 show_subscribe_session_notify)',
    notified_open         TINYINT  NOT NULL DEFAULT 0 COMMENT '历史字段,已弃用(按场次跟踪在 show_subscribe_session_notify)',
    create_time           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_show (user_id, show_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演出开售提醒订阅(用户订阅整个演出,系统按演出的每个场次分别推送)';

-- 订阅 × 场次推送跟踪表:订阅维度仍是"演出",但提醒按"场次"展开,本表跟踪
-- "某个订阅在某个场次的提前提醒/开售提醒"是否已推送,保证按场次幂等。
-- 同一演出多场次场景下,每场各自触发各自的提醒,不会因为旧的"演出级 notified_pre"漏掉后续场次。
CREATE TABLE IF NOT EXISTS show_subscribe_session_notify (
    subscribe_id  BIGINT   NOT NULL COMMENT '订阅 ID(关联 show_subscribe.id)',
    session_id    BIGINT   NOT NULL COMMENT '场次 ID(关联 show_session.id)',
    notified_pre  TINYINT  NOT NULL DEFAULT 0 COMMENT '已推送提前提醒:0=否 1=是',
    notified_open TINYINT  NOT NULL DEFAULT 0 COMMENT '已推送开售提醒:0=否 1=是',
    update_time   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (subscribe_id, session_id),
    KEY idx_session (session_id) COMMENT '场次删除时反查清理'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅×场次推送状态';

-- ------------------------------------------------------------
-- 消息主表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS message (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    type        TINYINT      NOT NULL COMMENT '1=订单 2=开售提醒 3=系统通知 4=互动 5=关注动态',
    title       VARCHAR(200) NOT NULL,
    content     TEXT         NOT NULL,
    link_type   TINYINT      NOT NULL DEFAULT 0 COMMENT '0=无 1=演出 2=艺人 3=资讯 4=订单 5=URL',
    link_target VARCHAR(500) NULL COMMENT 'target_id 或 URL',
    broadcast   TINYINT      NOT NULL DEFAULT 0 COMMENT '0=单发 1=广播',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_type_time (type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息主表';

-- ------------------------------------------------------------
-- 用户-消息关系表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_message (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    user_id     BIGINT   NOT NULL COMMENT '用户ID',
    message_id  BIGINT   NOT NULL COMMENT '消息ID',
    is_read     TINYINT  NOT NULL DEFAULT 0 COMMENT '0=未读 1=已读',
    read_at     DATETIME NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_msg (user_id, message_id),
    KEY idx_user_unread_time (user_id, is_read, create_time DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-消息关系';

-- ------------------------------------------------------------
-- 演出评价主表（一级评论 + 二级回复）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS show_review (
    id                BIGINT   NOT NULL AUTO_INCREMENT,
    show_id           BIGINT   NOT NULL COMMENT '演出ID',
    user_id           BIGINT   NOT NULL COMMENT '发布用户ID',
    order_id          BIGINT   NULL     COMMENT '订单ID,"已观看"模式必填',
    parent_id         BIGINT   NULL     COMMENT 'NULL=一级评论;否则=所属一级评论ID',
    reply_to_user_id  BIGINT   NULL     COMMENT '二级回复时@的目标用户ID',
    content           TEXT     NOT NULL,
    rating            TINYINT  NULL     COMMENT '1-5星,仅一级评论',
    like_count        INT      NOT NULL DEFAULT 0,
    reply_count       INT      NOT NULL DEFAULT 0 COMMENT '仅一级评论维护',
    status            TINYINT  NOT NULL DEFAULT 0 COMMENT '0=正常 1=举报中 2=被隐藏',
    create_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_show_parent_time (show_id, parent_id, create_time DESC),
    KEY idx_show_parent_hot (show_id, parent_id, like_count DESC),
    KEY idx_parent_time (parent_id, create_time),
    KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演出评价';

-- ------------------------------------------------------------
-- 评价图片表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS show_review_image (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    review_id   BIGINT       NOT NULL,
    url         VARCHAR(500) NOT NULL,
    sort        INT          NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_review_sort (review_id, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价图片';

-- ------------------------------------------------------------
-- 评价点赞表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS show_review_like (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    review_id   BIGINT   NOT NULL,
    user_id     BIGINT   NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_user (review_id, user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价点赞';

-- ------------------------------------------------------------
-- 评价举报表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS show_review_report (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    review_id   BIGINT       NOT NULL,
    reporter_id BIGINT       NOT NULL COMMENT '举报人',
    reason      VARCHAR(200) NOT NULL,
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0=待处理 1=已处理-保留 2=已处理-删除',
    handler_id  BIGINT       NULL     COMMENT 'admin 处理人',
    handled_at  DATETIME     NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reporter_review (reporter_id, review_id) COMMENT '同一用户对同一评论仅允许举报一次',
    KEY idx_status_time (status, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评价举报';

-- ------------------------------------------------------------
-- 艺人表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS artist (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(100) NOT NULL COMMENT '本名',
    stage_name    VARCHAR(100) NULL     COMMENT '艺名',
    avatar_url    VARCHAR(500) NULL,
    gender        TINYINT      NOT NULL DEFAULT 0 COMMENT '0=保密 1=男 2=女',
    nationality   VARCHAR(50)  NULL     COMMENT '国籍/地区',
    tags          VARCHAR(500) NULL     COMMENT '逗号分隔,如"歌手,演员"',
    bio           VARCHAR(500) NULL     COMMENT '简介短文本',
    description   LONGTEXT     NULL     COMMENT '富文本详介',
    social_links  JSON         NULL     COMMENT '{"weibo":"","instagram":"","x":""}',
    follow_count  INT          NOT NULL DEFAULT 0,
    show_count    INT          NOT NULL DEFAULT 0,
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '0=下架 1=上架',
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_name (name),
    KEY idx_stage_name (stage_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='艺人';

-- ------------------------------------------------------------
-- 演出-艺人关联表（多对多）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS show_artist (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    show_id     BIGINT      NOT NULL,
    artist_id   BIGINT      NOT NULL,
    role        VARCHAR(50) NULL COMMENT '角色,主演/导演/特邀等',
    sort        INT         NOT NULL DEFAULT 0,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_show_artist (show_id, artist_id),
    KEY idx_artist (artist_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='演出-艺人关联';

-- ------------------------------------------------------------
-- 用户关注艺人表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_follow_artist (
    id          BIGINT   NOT NULL AUTO_INCREMENT,
    user_id     BIGINT   NOT NULL,
    artist_id   BIGINT   NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_artist (user_id, artist_id),
    KEY idx_artist (artist_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注艺人';

-- ------------------------------------------------------------
-- 资讯分类表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS article_category (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(50) NOT NULL,
    sort        INT         NOT NULL DEFAULT 0,
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '0=下架 1=上架',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资讯分类';

-- ------------------------------------------------------------
-- 资讯表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS article (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    category_id  BIGINT       NOT NULL,
    title        VARCHAR(200) NOT NULL,
    summary      VARCHAR(500) NULL COMMENT '摘要,列表展示用',
    content      LONGTEXT     NOT NULL COMMENT '富文本',
    cover_url    VARCHAR(500) NULL,
    artist_id    BIGINT       NULL COMMENT '可关联艺人',
    author       VARCHAR(50)  NULL,
    view_count   INT          NOT NULL DEFAULT 0,
    status       TINYINT      NOT NULL DEFAULT 0 COMMENT '0=草稿 1=已发布 2=已下架',
    published_at DATETIME     NULL,
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_category_status_time (category_id, status, published_at DESC),
    KEY idx_artist (artist_id),
    KEY idx_status_time (status, published_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资讯';

-- ------------------------------------------------------------
-- Banner / 运营位表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS banner (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    title       VARCHAR(100) NULL COMMENT '后台备注,前端可不展示',
    image_url   VARCHAR(500) NOT NULL,
    link_type   TINYINT      NOT NULL DEFAULT 0 COMMENT '0=无 1=演出 2=艺人 3=资讯 4=外链',
    link_target VARCHAR(500) NULL,
    sort        INT          NOT NULL DEFAULT 0,
    start_at    DATETIME     NULL COMMENT '定时上架,NULL=立即生效',
    end_at      DATETIME     NULL COMMENT '定时下架,NULL=永久',
    status      TINYINT      NOT NULL DEFAULT 0 COMMENT '0=下架 1=上架',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_status_sort (status, sort)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页Banner/运营位';
