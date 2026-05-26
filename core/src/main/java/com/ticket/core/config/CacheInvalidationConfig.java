package com.ticket.core.config;

import com.ticket.core.cache.CacheInvalidationBroadcaster;
import com.ticket.core.cache.CacheInvalidationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Caffeine 缓存失效广播订阅配置。
 *
 * 每个实例都注册一个 {@link CacheInvalidationListener},订阅同一个 Redis channel,
 * 任何实例的写操作都通过 {@link CacheInvalidationBroadcaster} 广播,达成集群一致。
 */
@Configuration
public class CacheInvalidationConfig {

    @Bean
    public RedisMessageListenerContainer cacheInvalidationListenerContainer(
            RedisConnectionFactory connectionFactory,
            CacheInvalidationListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener,
                new ChannelTopic(CacheInvalidationBroadcaster.CHANNEL));
        return container;
    }
}
