package com.ticket.common.config;

import com.ticket.common.util.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Snowflake 配置：worker-id 与 data-center-id 必须显式配置（不提供默认值兜底）.
 *
 * 多副本部署时必须保证每个实例的 (workerId, dataCenterId) 组合全局唯一,
 * 否则同一毫秒并发生成的 ID 会冲突,导致 order_no/payment.id 主键冲突。
 */
@Slf4j
@Configuration
public class SnowflakeConfig {

    /** 不提供默认值,缺失时 Spring 启动直接报错 */
    @Value("${snowflake.worker-id}")
    private long workerId;

    @Value("${snowflake.data-center-id}")
    private long dataCenterId;

    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(Environment env) {
        String appName = env.getProperty("spring.application.name", "unknown");
        String[] activeProfiles = env.getActiveProfiles();
        boolean isProd = false;
        for (String p : activeProfiles) {
            if ("prod".equalsIgnoreCase(p)) { isProd = true; break; }
        }
        // 生产环境若仍使用 workerId=1, dataCenterId=1 的默认组合,提示风险(可能是环境变量未注入)
        if (isProd && workerId == 1 && dataCenterId == 1) {
            log.warn("[Snowflake] 生产环境检测到 workerId=1, dataCenterId=1 的默认组合, " +
                    "请确认 SNOWFLAKE_WORKER_ID 环境变量已正确注入(每副本唯一)! app={}", appName);
        }
        log.info("[Snowflake] 初始化 app={} workerId={} dataCenterId={}",
                appName, workerId, dataCenterId);
        return new SnowflakeIdGenerator(workerId, dataCenterId);
    }
}
