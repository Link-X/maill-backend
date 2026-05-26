package com.ticket.core.service;

import com.ticket.common.constant.RedisKeys;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.ErrorCode;
import com.ticket.core.domain.entity.Article;
import com.ticket.core.domain.entity.ArticleCategory;
import com.ticket.core.domain.entity.Artist;
import com.ticket.core.mapper.ArticleCategoryMapper;
import com.ticket.core.mapper.ArticleMapper;
import com.ticket.core.mapper.ArtistMapper;
import com.ticket.core.mq.event.SearchSyncEvent;
import com.ticket.core.mq.producer.SearchSyncProducer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ArticleService {

    private final ArticleMapper articleMapper;
    private final ArticleCategoryMapper categoryMapper;
    private final ArtistMapper artistMapper;
    private final SearchSyncProducer searchSyncProducer;
    private final StringRedisTemplate redis;

    public ArticleService(ArticleMapper articleMapper,
                          ArticleCategoryMapper categoryMapper,
                          ArtistMapper artistMapper,
                          SearchSyncProducer searchSyncProducer,
                          StringRedisTemplate redis) {
        this.articleMapper = articleMapper;
        this.categoryMapper = categoryMapper;
        this.artistMapper = artistMapper;
        this.searchSyncProducer = searchSyncProducer;
        this.redis = redis;
    }

    @Transactional
    public Article save(Article article) {
        LocalDateTime now = LocalDateTime.now();
        if (article.getStatus() == null) article.setStatus(0);
        if (article.getViewCount() == null) article.setViewCount(0);

        if (article.getId() == null) {
            article.setCreateTime(now);
            article.setUpdateTime(now);
            articleMapper.insert(article);
            // 发送搜索同步事件
            searchSyncProducer.sendAfterCommit(SearchSyncEvent.upsert("article", article.getId()));
            return article;
        }
        Article exist = articleMapper.selectById(article.getId());
        if (exist == null) throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        article.setUpdateTime(now);
        articleMapper.update(article);
        // 发送搜索同步事件
        searchSyncProducer.sendAfterCommit(SearchSyncEvent.upsert("article", article.getId()));
        return articleMapper.selectById(article.getId());
    }

    @Transactional
    public void publish(Long id) {
        Article exist = articleMapper.selectById(id);
        if (exist == null) throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime publishedAt = exist.getPublishedAt() != null ? exist.getPublishedAt() : now;
        articleMapper.updateStatus(id, 1, publishedAt, now);
        // 发布后同步到 ES
        searchSyncProducer.sendAfterCommit(SearchSyncEvent.upsert("article", id));
    }

    @Transactional
    public void offline(Long id) {
        Article exist = articleMapper.selectById(id);
        if (exist == null) throw new BusinessException(ErrorCode.ARTICLE_NOT_FOUND);
        articleMapper.updateStatus(id, 2, exist.getPublishedAt(), LocalDateTime.now());
        // 下架后同步到 ES（status 变化）
        searchSyncProducer.sendAfterCommit(SearchSyncEvent.upsert("article", id));
    }

    @Transactional
    public void delete(Long id) {
        articleMapper.deleteById(id);
        // 同步删除 ES 文档
        searchSyncProducer.sendAfterCommit(SearchSyncEvent.delete("article", id));
    }

    public Article getById(Long id) {
        Article a = articleMapper.selectById(id);
        if (a != null) joinRelations(a);
        return a;
    }

    /**
     * user 端详情：DB 取主体 + Redis HINCRBY 累计本次浏览(避免热门文章 DB 行锁热点)。
     * 返回的 viewCount = DB.view_count + Redis 缓冲区累计值，前端展示无延迟。
     * 缓冲区由 ArticleViewFlushScheduler 定时回写 DB 并清零。
     */
    public Article getByIdAndIncrView(Long id) {
        Article a = articleMapper.selectById(id);
        if (a == null) return null;
        long delta = 0L;
        try {
            Long incr = redis.opsForHash().increment(
                    RedisKeys.articleViewBuffer(), String.valueOf(id), 1L);
            delta = incr == null ? 0L : incr;
        } catch (Exception e) {
            // Redis 异常不阻塞详情接口；本次 view 计数丢失可接受
        }
        int dbView = a.getViewCount() == null ? 0 : a.getViewCount();
        a.setViewCount(dbView + (int) delta);
        joinRelations(a);
        return a;
    }

    public Map<String, Object> listByCondition(Integer status, Long categoryId, String keyword,
                                               Integer page, Integer size) {
        Integer offset = (page != null && size != null) ? (page - 1) * size : null;
        List<Article> list = articleMapper.selectByCondition(status, categoryId, keyword, offset, size);
        int total = articleMapper.countByCondition(status, categoryId, keyword);
        joinRelationsBatch(list);
        Map<String, Object> r = new HashMap<>();
        r.put("list", list);
        r.put("total", total);
        r.put("page", page);
        r.put("size", size);
        return r;
    }

    public Map<String, Object> listPublished(Long categoryId, Integer page, Integer size) {
        Integer offset = (page != null && size != null) ? (page - 1) * size : null;
        List<Article> list = articleMapper.selectPublished(categoryId, offset, size);
        int total = articleMapper.countPublished(categoryId);
        joinRelationsBatch(list);
        Map<String, Object> r = new HashMap<>();
        r.put("list", list);
        r.put("total", total);
        r.put("page", page);
        r.put("size", size);
        return r;
    }

    public List<Article> listByArtist(Long artistId, Integer page, Integer size) {
        Integer offset = (page != null && size != null) ? (page - 1) * size : null;
        List<Article> list = articleMapper.selectByArtistId(artistId, offset, size);
        joinRelationsBatch(list);
        return list;
    }

    private void joinRelations(Article a) {
        if (a.getCategoryId() != null) {
            ArticleCategory c = categoryMapper.selectById(a.getCategoryId());
            a.setCategory(c);
        }
        if (a.getArtistId() != null) {
            Artist ar = artistMapper.selectById(a.getArtistId());
            a.setArtist(ar);
        }
    }

    private void joinRelationsBatch(List<Article> list) {
        for (Article a : list) joinRelations(a);
    }
}
