package com.ticket.core.mq.consumer;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.common.es.index.EsIndices;
import com.ticket.core.domain.entity.Article;
import com.ticket.core.domain.entity.Artist;
import com.ticket.core.domain.entity.Category;
import com.ticket.core.domain.entity.City;
import com.ticket.core.domain.entity.Show;
import com.ticket.core.mapper.ArticleMapper;
import com.ticket.core.mapper.ArtistMapper;
import com.ticket.core.mapper.CategoryMapper;
import com.ticket.core.mapper.CityMapper;
import com.ticket.core.mapper.ShowArtistMapper;
import com.ticket.core.mapper.ShowMapper;
import com.ticket.core.mq.config.RabbitMQConfig;
import com.ticket.core.mq.event.SearchSyncEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 搜索同步消费者：接收 SearchSyncEvent → 读 DB → 写 ES。
 */
@Slf4j
@Component
public class SearchSyncConsumer {

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ElasticsearchClient esClient;
    private final ObjectMapper objectMapper;
    private final ShowMapper showMapper;
    private final ShowArtistMapper showArtistMapper;
    private final ArtistMapper artistMapper;
    private final ArticleMapper articleMapper;
    private final CategoryMapper categoryMapper;
    private final CityMapper cityMapper;

    public SearchSyncConsumer(ElasticsearchClient esClient,
                              ObjectMapper objectMapper,
                              ShowMapper showMapper,
                              ShowArtistMapper showArtistMapper,
                              ArtistMapper artistMapper,
                              ArticleMapper articleMapper,
                              CategoryMapper categoryMapper,
                              CityMapper cityMapper) {
        this.esClient = esClient;
        this.objectMapper = objectMapper;
        this.showMapper = showMapper;
        this.showArtistMapper = showArtistMapper;
        this.artistMapper = artistMapper;
        this.articleMapper = articleMapper;
        this.categoryMapper = categoryMapper;
        this.cityMapper = cityMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.SEARCH_SYNC_QUEUE)
    public void onMessage(SearchSyncEvent event) {
        if (event == null || event.getType() == null || event.getTargetId() == null) {
            log.warn("[MQ-SEARCH] 空消息或缺字段,忽略 event={}", event);
            return;
        }
        try {
            String op = event.getOp() == null ? "UPSERT" : event.getOp();
            String type = event.getType();
            Long id = event.getTargetId();
            if ("DELETE".equals(op)) {
                deleteDoc(type, id);
                log.info("[MQ-SEARCH] DELETE type={} id={}", type, id);
                return;
            }
            switch (type) {
                case "show":    upsertShow(id); break;
                case "artist":  upsertArtist(id); break;
                case "article": upsertArticle(id); break;
                default: log.warn("[MQ-SEARCH] unknown type={}", type);
            }
            log.info("[MQ-SEARCH] UPSERT type={} id={}", type, id);
        } catch (Exception e) {
            log.error("[MQ-SEARCH] sync fail event={} err={}", event, e.getMessage(), e);
            // 简化：不抛异常导致重试。生产可改 nack 重试或写死信。
        }
    }

    private void deleteDoc(String type, Long id) throws Exception {
        String index = indexFor(type);
        if (index == null) return;
        esClient.delete(d -> d.index(index).id(String.valueOf(id)));
    }

    private String indexFor(String type) {
        switch (type) {
            case "show": return EsIndices.SHOW;
            case "artist": return EsIndices.ARTIST;
            case "article": return EsIndices.ARTICLE;
            default: return null;
        }
    }

    private void upsertShow(Long id) throws Exception {
        Show s = showMapper.selectById(id);
        if (s == null) { deleteDoc("show", id); return; }
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", s.getId());
        doc.put("name", s.getName());
        doc.put("description", s.getDescription());
        doc.put("venue", s.getVenue());
        doc.put("category_id", s.getCategoryId());
        if (s.getCategoryId() != null) {
            Category cat = categoryMapper.selectById(s.getCategoryId());
            if (cat != null) doc.put("category_name", cat.getName());
        }
        if (s.getCityCode() != null) {
            City city = cityMapper.selectByCode(s.getCityCode());
            if (city != null) {
                doc.put("city_code", city.getCode());
                doc.put("city_name", city.getName());
            }
        }
        doc.put("poster_url", s.getPosterUrl());
        doc.put("status", s.getStatus());
        if (s.getCreateTime() != null) doc.put("create_time", s.getCreateTime().format(DTF));
        doc.put("avg_rating", s.getAvgRating());
        doc.put("review_count", s.getReviewCount());

        indexDoc(EsIndices.SHOW, id, doc);
    }

    private void upsertArtist(Long id) throws Exception {
        Artist a = artistMapper.selectById(id);
        if (a == null) { deleteDoc("artist", id); return; }
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", a.getId());
        doc.put("name", a.getName());
        doc.put("stage_name", a.getStageName());
        doc.put("avatar_url", a.getAvatarUrl());
        doc.put("nationality", a.getNationality());
        doc.put("tags", a.getTags());
        doc.put("bio", a.getBio());
        doc.put("status", a.getStatus());
        if (a.getCreateTime() != null) doc.put("create_time", a.getCreateTime().format(DTF));
        indexDoc(EsIndices.ARTIST, id, doc);
    }

    private void upsertArticle(Long id) throws Exception {
        Article art = articleMapper.selectById(id);
        if (art == null) { deleteDoc("article", id); return; }
        Map<String, Object> doc = new HashMap<>();
        doc.put("id", art.getId());
        doc.put("title", art.getTitle());
        doc.put("summary", art.getSummary());
        // content 也索引以便正文搜索；如担心索引体积可以只存摘要
        doc.put("content", art.getContent());
        doc.put("cover_url", art.getCoverUrl());
        doc.put("category_id", art.getCategoryId());
        doc.put("artist_id", art.getArtistId());
        doc.put("author", art.getAuthor());
        doc.put("status", art.getStatus());
        if (art.getPublishedAt() != null) doc.put("published_at", art.getPublishedAt().format(DTF));
        if (art.getCreateTime() != null) doc.put("create_time", art.getCreateTime().format(DTF));
        indexDoc(EsIndices.ARTICLE, id, doc);
    }

    private void indexDoc(String index, Long id, Map<String, Object> doc) throws Exception {
        esClient.index(i -> i
                .index(index)
                .id(String.valueOf(id))
                .document(doc));
    }
}
