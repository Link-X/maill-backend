package com.ticket.common.exception;

/**
 * 错误码枚举.
 *
 * 定义系统级错误码(500/400/401 等)和业务级错误码(1001~1008).
 * 业务错误码以 1xxx 开头,与 HTTP 状态码区分.
 */
public enum ErrorCode {

    SYSTEM_ERROR(500, "系统内部错误"),
    PARAM_ERROR(400, "参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),

    // --- 业务错误码 ---
    SEAT_NOT_AVAILABLE(1001, "座位不可用"),
    EXCEED_PURCHASE_LIMIT(1002, "超过限购数量"),
    TICKET_ALREADY_USED(1003, "票已使用"),
    TICKET_EXPIRED(1004, "票已过期"),
    ORDER_NOT_FOUND(1005, "订单不存在"),
    ORDER_EXPIRED(1006, "订单已过期"),
    SESSION_NOT_FOUND(1007, "场次不存在"),
    SHOW_NOT_FOUND(1008, "演出不存在"),
    SESSION_NOT_ON_SALE(1014, "场次未开售"),
    SESSION_ALREADY_ENDED(1015, "场次已结束"),
    RATE_LIMIT_EXCEEDED(1009, "系统繁忙，请稍后重试"),
    REFUND_TOO_CLOSE_TO_START(1010, "距演出开始不足1天，无法退款"),
    REFUND_ALL_TICKETS_USED(1011, "所有票券均已使用，无法退款"),
    CATEGORY_IN_USE(1012, "分类已被演出引用，无法删除"),
    CATEGORY_NAME_DUPLICATED(1013, "分类名已存在"),
    BANNER_NOT_FOUND(1020, "Banner 不存在"),
    ARTIST_NOT_FOUND(1030, "艺人不存在"),
    ARTIST_NAME_DUPLICATED(1031, "艺人名已存在"),
    ARTICLE_CATEGORY_NOT_FOUND(1040, "资讯分类不存在"),
    ARTICLE_CATEGORY_NAME_DUPLICATED(1041, "资讯分类名已存在"),
    ARTICLE_CATEGORY_IN_USE(1042, "资讯分类已被资讯引用,无法删除"),
    ARTICLE_NOT_FOUND(1050, "资讯不存在"),
    MESSAGE_NOT_FOUND(1060, "消息不存在"),
    FAVORITE_GROUP_NOT_FOUND(1070, "收藏分组不存在"),
    FAVORITE_GROUP_NAME_DUPLICATED(1071, "分组名已存在"),
    SUBSCRIBE_NOT_FOUND(1080, "订阅不存在"),
    REVIEW_NOT_FOUND(1090, "评价不存在"),
    REVIEW_NO_PERMISSION(1091, "无权评价该演出"),
    REVIEW_DISABLED(1092, "该演出未开放评价"),
    REVIEW_PARENT_NOT_FOUND(1093, "回复的评论不存在"),
    REVIEW_RATING_REQUIRED(1094, "请填写评分"),
    REVIEW_DELETE_FORBIDDEN(1095, "无权删除该评价"),
    REVIEW_REPORT_DUPLICATED(1096, "你已举报过该评价"),

    // --- 派座(混合模式)相关 ---
    STOCK_NOT_ENOUGH(1100, "库存不足"),
    AREA_SALE_MODE_MISMATCH(1101, "区域售卖模式不匹配"),

    // --- 场地模板相关 ---
    ROOM_NOT_FOUND(1110, "场地不存在"),
    ROOM_IN_USE(1111, "场地已被场次引用,无法删除");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
