package com.ticket.common.es;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Elasticsearch 配置属性
 * 通过 application-*.yml 的 elasticsearch.* 注入
 */
@Data
@Component
@ConfigurationProperties(prefix = "elasticsearch")
public class ElasticsearchProperties {

    /** ES HTTP 地址,如 localhost:9200 */
    private String host = "localhost:9200";

    /** 连接超时(毫秒) */
    private int connectTimeoutMs = 3000;

    /** 读取超时(毫秒) */
    private int socketTimeoutMs = 10000;
}
