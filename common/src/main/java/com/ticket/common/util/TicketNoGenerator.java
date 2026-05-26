package com.ticket.common.util;

import java.security.SecureRandom;

/**
 * 票号生成器.
 *
 * - generate(): 兼容旧调用,纯随机短码(可能极小概率冲突,业务层兜底查重)
 * - fromId(snowflakeId): 基于 Snowflake 主键确定性编码为短票号,无需查重(优先使用)
 *
 * 字符集排除易混淆 O/0/I/1,使用 Crockford base32 alphabet 变体.
 */
public class TicketNoGenerator {

    /** 32 字符,排除 O/0/I/1 易混字符 */
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int RANDOM_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String generate() {
        StringBuilder sb = new StringBuilder(9);
        sb.append("TK");
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    /**
     * 基于 Snowflake ID 生成确定性票号: "TK" + base32(id).
     *
     * Snowflake 全局唯一 → 票号天然唯一,业务层无需 SELECT 查重循环.
     * 长度: "TK" + 13 字符 = 15 字符,满足 ticket_no VARCHAR(16).
     */
    public static String fromId(long snowflakeId) {
        long value = snowflakeId < 0 ? Long.MAX_VALUE + snowflakeId + 1 : snowflakeId;
        char[] buf = new char[13];
        for (int i = 12; i >= 0; i--) {
            buf[i] = CHARS.charAt((int) (value & 31L));
            value >>>= 5;
        }
        return "TK" + new String(buf);
    }
}
