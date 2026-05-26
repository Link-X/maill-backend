package com.ticket.core.cache;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 缓存失效广播发布端。
 *
 * 通过 Redis pub/sub 把失效消息推给集群内所有实例(包括自己),
 * 各实例收到后在 {@link CacheInvalidationListener} 中清自己的 Caffeine。
 *
 * 消息格式:简单字符串 "TYPE:id",避免序列化开销。
 *  - SEATS:{sessionId}
 *  - AREAS:{sessionId}
 *  - SHOW:{showId}
 */
@Component
public class CacheInvalidationBroadcaster {

    public static final String CHANNEL = "cache:invalidate";

    public static final String TYPE_SEATS = "SEATS";
    public static final String TYPE_AREAS = "AREAS";
    public static final String TYPE_SHOW = "SHOW";

    private final StringRedisTemplate redisTemplate;

    public CacheInvalidationBroadcaster(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void invalidateSeats(Long sessionId) {
        publish(TYPE_SEATS, sessionId);
    }

    public void invalidateAreas(Long sessionId) {
        publish(TYPE_AREAS, sessionId);
    }

    public void invalidateShow(Long showId) {
        publish(TYPE_SHOW, showId);
    }

    private void publish(String type, Long id) {
        redisTemplate.convertAndSend(CHANNEL, type + ":" + id);
    }
}
