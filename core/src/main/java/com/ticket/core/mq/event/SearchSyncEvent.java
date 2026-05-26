package com.ticket.core.mq.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 搜索同步事件
 * 业务侧(admin 演出/艺人/资讯 save/delete)发送此事件,
 * core.SearchSyncConsumer 消费后写 ES。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchSyncEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 索引类型:show / artist / article */
    private String type;

    /** 主键 ID(用于从 DB 拉数据写 ES,或删除指定 doc) */
    private Long targetId;

    /** 操作:UPSERT / DELETE */
    private String op;

    public static SearchSyncEvent upsert(String type, Long id) {
        return new SearchSyncEvent(type, id, "UPSERT");
    }

    public static SearchSyncEvent delete(String type, Long id) {
        return new SearchSyncEvent(type, id, "DELETE");
    }
}
