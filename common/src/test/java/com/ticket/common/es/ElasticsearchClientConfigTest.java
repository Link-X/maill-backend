package com.ticket.common.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.InfoResponse;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.ticket.common.es.index.EsIndices;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.ComponentScan;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Elasticsearch 集成测试
 * 前提:本地 docker-compose up -d elasticsearch
 * 关闭方式:不设 -Des.it=true 即跳过
 */
@SpringBootTest(classes = ElasticsearchClientConfigTest.TestApp.class)
@EnabledIfSystemProperty(named = "es.it", matches = "true")
class ElasticsearchClientConfigTest {

    @SpringBootApplication
    @EnableAutoConfiguration
    @ComponentScan(basePackages = "com.ticket.common.es")
    static class TestApp {}

    @Autowired
    ElasticsearchClient client;

    @Test
    void should_ping_elasticsearch() throws Exception {
        InfoResponse info = client.info();
        String version = info.version().number();
        // 升级到 ES 8 客户端,允许 7.x / 8.x
        assertTrue(version.startsWith("7.") || version.startsWith("8."),
                "期望 ES 7.x 或 8.x,实际:" + version);
    }

    @Test
    void should_create_three_indices() throws Exception {
        assertTrue(indexExists(EsIndices.SHOW),    "show 索引应该存在");
        assertTrue(indexExists(EsIndices.ARTIST),  "artist 索引应该存在");
        assertTrue(indexExists(EsIndices.ARTICLE), "article 索引应该存在");
    }

    private boolean indexExists(String name) throws Exception {
        return client.indices().exists(ExistsRequest.of(b -> b.index(name))).value();
    }
}
