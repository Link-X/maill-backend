package com.ticket.core.cache;

import com.ticket.core.service.SeatStructureCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 接收 {@link CacheInvalidationBroadcaster} 广播的失效消息,清本实例的 Caffeine。
 *
 * 注意:发布者本机也会收到自己发的消息,代码统一(不区分本机/远端)。
 * Redis pub/sub 投递延迟通常 < 5ms,可以接受。
 */
@Slf4j
@Component
public class CacheInvalidationListener implements MessageListener {

    private final SeatStructureCache structureCache;

    public CacheInvalidationListener(SeatStructureCache structureCache) {
        this.structureCache = structureCache;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        int sep = body.indexOf(':');
        if (sep <= 0 || sep == body.length() - 1) {
            log.warn("缓存失效消息格式异常: {}", body);
            return;
        }
        String type = body.substring(0, sep);
        long id;
        try {
            id = Long.parseLong(body.substring(sep + 1));
        } catch (NumberFormatException e) {
            log.warn("缓存失效消息 id 解析失败: {}", body);
            return;
        }
        switch (type) {
            case CacheInvalidationBroadcaster.TYPE_SEATS -> structureCache.invalidateSeats(id);
            case CacheInvalidationBroadcaster.TYPE_AREAS -> structureCache.invalidateAreas(id);
            case CacheInvalidationBroadcaster.TYPE_SHOW -> structureCache.invalidateShow(id);
            default -> log.warn("未知缓存失效类型: {}", type);
        }
    }
}
