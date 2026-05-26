package com.ticket.core.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.redis.spring.RedisLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * ShedLock 分布式锁配置。
 *
 * 让所有 JVM 都注册 @Scheduled Bean,但每次执行时通过 Redis SETNX 抢锁:
 *  - 抢到锁的实例真正跑任务
 *  - 没抢到的直接 skip
 *  - 抢到锁的实例挂了,锁 TTL 到期后下次任务由其他实例接管(故障转移)
 *
 * defaultLockAtMostFor: 锁的最大持有时间。即使任务异常没释放锁,过这个时间也会自动释放,
 *   避免单次任务卡死后所有调度都死。值要大于任务最长执行时间。
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT5M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(RedisConnectionFactory connectionFactory) {
        return new RedisLockProvider(connectionFactory, "ticket-shedlock");
    }
}
