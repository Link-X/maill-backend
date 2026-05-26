package com.ticket.core.scheduler;

import com.ticket.common.constant.RedisKeys;
import com.ticket.core.mapper.ArticleMapper;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 把资讯浏览数 Redis 缓冲区(article:view:buffer)按定时回写 DB。
 *
 * 每 5 分钟一次：对每个 articleId,先 HGET 取增量,再 HINCRBY -delta(原子减),
 * 然后 UPDATE article SET view_count = view_count + delta。
 *
 * HINCRBY -delta 是原子操作：即使期间有新的浏览 +1,只会减去本次回写的部分,
 * 新增量留在 Redis 等待下次 flush,不会丢失。
 *
 * 多实例互斥由 @SchedulerLock(ShedLock)统一处理,无需手动写 SETNX。
 */
@Slf4j
@Component
public class ArticleViewFlushScheduler {

    private final StringRedisTemplate redis;
    private final ArticleMapper articleMapper;

    public ArticleViewFlushScheduler(StringRedisTemplate redis, ArticleMapper articleMapper) {
        this.redis = redis;
        this.articleMapper = articleMapper;
    }

    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 60 * 1000)
    @SchedulerLock(name = "ArticleViewFlushScheduler.flush",
            lockAtLeastFor = "PT1M", lockAtMostFor = "PT4M")
    public void flush() {
        try {
            String bufferKey = RedisKeys.articleViewBuffer();
            Map<Object, Object> all = redis.opsForHash().entries(bufferKey);
            if (all == null || all.isEmpty()) return;

            int success = 0;
            for (Map.Entry<Object, Object> entry : all.entrySet()) {
                String field = String.valueOf(entry.getKey());
                long delta;
                try {
                    delta = Long.parseLong(String.valueOf(entry.getValue()));
                } catch (NumberFormatException e) {
                    continue;
                }
                if (delta <= 0) continue;
                // 原子减去本次回写量；新累加的留在 Redis
                redis.opsForHash().increment(bufferKey, field, -delta);
                try {
                    long articleId = Long.parseLong(field);
                    articleMapper.incrViewCountBy(articleId, delta);
                    success++;
                } catch (Exception e) {
                    // 回写 DB 失败：把减掉的加回去,下次再试
                    redis.opsForHash().increment(bufferKey, field, delta);
                    log.warn("[ARTICLE-VIEW-FLUSH] fail field={} delta={} err={}",
                            field, delta, e.getMessage());
                }
            }
            log.info("[ARTICLE-VIEW-FLUSH] flushed {} articles", success);
        } catch (Exception e) {
            log.error("[ARTICLE-VIEW-FLUSH] unexpected error", e);
        }
    }
}
