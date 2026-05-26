package com.ticket.common.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.ticket.common.es.index.ArticleIndexMapping;
import com.ticket.common.es.index.ArtistIndexMapping;
import com.ticket.common.es.index.EsIndices;
import com.ticket.common.es.index.ShowIndexMapping;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.StringReader;

/**
 * 应用启动时确保 ES 索引存在(不存在则创建,存在跳过)
 * 三个 Spring Boot 应用启动都会触发,但用 indices.exists() 做了幂等保护。
 */
@Slf4j
@Component
public class IndexInitializer implements ApplicationRunner {

    private final ElasticsearchClient client;

    public IndexInitializer(ElasticsearchClient client) {
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            ensureIndex(EsIndices.SHOW,    ShowIndexMapping.JSON);
            ensureIndex(EsIndices.ARTIST,  ArtistIndexMapping.JSON);
            ensureIndex(EsIndices.ARTICLE, ArticleIndexMapping.JSON);
        } catch (Exception e) {
            // ES 不可用时不阻塞应用启动,只记日志(后续业务用 try-catch 降级)
            log.warn("[ES] 初始化索引失败,稍后业务调用时会重试: {}", e.getMessage());
        }
    }

    private void ensureIndex(String name, String mappingJson) throws Exception {
        boolean exists = client.indices().exists(ExistsRequest.of(b -> b.index(name))).value();
        if (exists) {
            log.info("[ES] 索引已存在,跳过: {}", name);
            return;
        }
        // withJson 接收完整的 create index body（settings + mappings），与旧 API 的 source(json) 行为一致
        client.indices().create(c -> c
                .index(name)
                .withJson(new StringReader(mappingJson)));
        log.info("[ES] 索引创建成功: {}", name);
    }
}
