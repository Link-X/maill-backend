package com.ticket.common.es;

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch RestHighLevelClient 配置
 * 注意:Spring Boot 2.7 默认提供 RestHighLevelClient(7.17.x),
 * 此处显式配置以便统一管理超时和地址。
 */
@Configuration
public class ElasticsearchClientConfig {

    private final ElasticsearchProperties properties;

    public ElasticsearchClientConfig(ElasticsearchProperties properties) {
        this.properties = properties;
    }

    @Bean(destroyMethod = "close")
    public RestHighLevelClient elasticsearchClient() {
        String[] parts = properties.getHost().split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9200;

        RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, "http"))
                .setRequestConfigCallback(req -> req
                        .setConnectTimeout(properties.getConnectTimeoutMs())
                        .setSocketTimeout(properties.getSocketTimeoutMs()));

        return new RestHighLevelClient(builder);
    }
}
