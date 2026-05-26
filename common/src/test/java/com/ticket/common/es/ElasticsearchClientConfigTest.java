package com.ticket.common.es;

import com.ticket.common.es.index.EsIndices;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.MainResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
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
    RestHighLevelClient client;

    @Test
    void should_ping_elasticsearch() throws Exception {
        MainResponse info = client.info(RequestOptions.DEFAULT);
        assertTrue(info.getVersion().getNumber().startsWith("7."),
                "期望 ES 7.x,实际:" + info.getVersion().getNumber());
    }

    @Test
    void should_create_three_indices() throws Exception {
        assertTrue(indexExists(EsIndices.SHOW),    "show 索引应该存在");
        assertTrue(indexExists(EsIndices.ARTIST),  "artist 索引应该存在");
        assertTrue(indexExists(EsIndices.ARTICLE), "article 索引应该存在");
    }

    private boolean indexExists(String name) throws Exception {
        return client.indices().exists(new GetIndexRequest(name), RequestOptions.DEFAULT);
    }
}
