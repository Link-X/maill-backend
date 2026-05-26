package com.ticket.common.es;

import com.ticket.common.es.index.ArticleIndexMapping;
import com.ticket.common.es.index.ArtistIndexMapping;
import com.ticket.common.es.index.EsIndices;
import com.ticket.common.es.index.ShowIndexMapping;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 应用启动时确保 ES 索引存在(不存在则创建,存在跳过)
 * 三个 Spring Boot 应用启动都会触发,但用 indices.exists() 做了幂等保护。
 */
@Slf4j
@Component
public class IndexInitializer implements ApplicationRunner {

    private final RestHighLevelClient client;

    public IndexInitializer(RestHighLevelClient client) {
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
        GetIndexRequest exists = new GetIndexRequest(name);
        if (client.indices().exists(exists, RequestOptions.DEFAULT)) {
            log.info("[ES] 索引已存在,跳过: {}", name);
            return;
        }
        CreateIndexRequest create = new CreateIndexRequest(name);
        create.source(mappingJson, XContentType.JSON);
        client.indices().create(create, RequestOptions.DEFAULT);
        log.info("[ES] 索引创建成功: {}", name);
    }
}
