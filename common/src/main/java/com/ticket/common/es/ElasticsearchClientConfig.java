package com.ticket.common.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Elasticsearch Java API Client 配置（Spring Boot 3 / ES 8 推荐客户端）
 * 取代已废弃的 RestHighLevelClient。
 */
@Configuration
public class ElasticsearchClientConfig {

    private final ElasticsearchProperties properties;

    public ElasticsearchClientConfig(ElasticsearchProperties properties) {
        this.properties = properties;
    }

    /** 底层 HTTP 客户端，单独暴露便于资源释放与超时复用 */
    @Bean(destroyMethod = "close")
    public RestClient restClient() {
        String[] parts = properties.getHost().split(":");
        String host = parts[0];
        int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9200;

        RestClientBuilder builder = RestClient.builder(new HttpHost(host, port, "http"))
                .setRequestConfigCallback(req -> req
                        .setConnectTimeout(properties.getConnectTimeoutMs())
                        .setSocketTimeout(properties.getSocketTimeoutMs()));
        return builder.build();
    }

    @Bean
    public ElasticsearchTransport elasticsearchTransport(RestClient restClient) {
        return new RestClientTransport(restClient, new JacksonJsonpMapper());
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(ElasticsearchTransport transport) {
        return new ElasticsearchClient(transport);
    }
}
