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
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(128) NOT NULL COMMENT '演出名称',
    description TEXT                  COMMENT '演出描述',
    category_id BIGINT                DEFAULT NULL COMMENT '关联 category.id',
    city_code   VARCHAR(10)           DEFAULT NULL COMMENT '关联 city.code (GB/T 行政区划代码)',
    address     VARCHAR(255)          DEFAULT NULL COMMENT '详细地址',
    poster_url  VARCHAR(512)          DEFAULT NULL COMMENT '海报URL',
    venue       VARCHAR(256)          DEFAULT NULL COMMENT '演出场馆名',
    status      INT          NOT NULL DEFAULT 0 COMMENT '状态: 0=草稿, 1=已上架, 2=已下架',
    extend      JSON                  DEFAULT NULL COMMENT '扩展字段（如预售/退改规则/时长/适宜年龄），约定见前端文档；不参与 WHERE/索引',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_status_create_time (status, create_time) COMMENT '用于演出列表按状态分页查询，避免 filesort',
    KEY idx_name        (name)        COMMENT '搜索: name LIKE xxx%',
    KEY idx_venue       (venue)       COMMENT '搜索: venue LIKE xxx%',
    KEY idx_category_id (category_id) COMMENT '按分类筛选',
    KEY idx_city_code   (city_code)   COMMENT '按城市筛选'
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
    status         INT          NOT NULL DEFAULT 0 COMMENT '状态: 0=未开放, 1=销售中, 2=已结束, 3=已预热',
    row_count      INT          NOT NULL DEFAULT 0 COMMENT '座位网格行数',
    col_count      INT          NOT NULL DEFAULT 0 COMMENT '座位网格列数',
    extend         JSON                  DEFAULT NULL COMMENT '扩展字段（如开售提前N分钟/特殊提示），不参与 WHERE/索引',
    create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_show_id (show_id)
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
CREATE TABLE IF NOT EXISTS seat_area (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    session_id  BIGINT       NOT NULL COMMENT '关联场次ID',
    area_id     VARCHAR(32)  NOT NULL COMMENT '区域标识(如 0、1，场次内唯一)',
    price       DECIMAL(10,2) NOT NULL COMMENT '区域售价',
    origin_price DECIMAL(10,2) NOT NULL COMMENT '区域原价',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_area (session_id, area_id),
    KEY idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='座位价格区域表';

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
INSERT INTO city (code, name, sort, status) VALUES
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
-- 存量数据库迁移（仅首次升级时执行，新建库忽略）
-- ============================================================
-- ALTER TABLE show_session ADD COLUMN room_id BIGINT DEFAULT NULL
--     COMMENT '关联场地ID，不为空时座位由场地模板复制' AFTER show_id;
