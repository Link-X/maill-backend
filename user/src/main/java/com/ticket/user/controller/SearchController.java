package com.ticket.user.controller;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Highlight;
import co.elastic.clients.elasticsearch.core.search.HighlightField;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticket.common.es.index.EsIndices;
import com.ticket.common.result.Result;
import com.ticket.user.config.NoLogin;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;

@Tag(name = "搜索", description = "搜索演出 / 艺人 / 资讯 + 搜索历史")
// 搜索本身允许游客访问；历史记录依赖登录态(未登录时 historyKey() 返回 null 自动跳过)
@NoLogin
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private static final int HISTORY_MAX = 10;
    private final ElasticsearchClient esClient;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;

    public SearchController(ElasticsearchClient esClient,
                            ObjectMapper objectMapper,
                            StringRedisTemplate redis) {
        this.esClient = esClient;
        this.objectMapper = objectMapper;
        this.redis = redis;
    }

    @Operation(summary = "搜索演出")
    @GetMapping("/show")
    public Result<Map<String, Object>> searchShow(
            @RequestParam String kw,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) throws Exception {
        saveHistory(kw);
        return Result.success(search(EsIndices.SHOW, kw, page, size,
                Arrays.asList("name", "name.kw", "description", "venue", "category_name", "city_name"),
                Arrays.asList("name", "description", "venue")));
    }

    @Operation(summary = "搜索艺人")
    @GetMapping("/artist")
    public Result<Map<String, Object>> searchArtist(
            @RequestParam String kw,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) throws Exception {
        saveHistory(kw);
        return Result.success(search(EsIndices.ARTIST, kw, page, size,
                Arrays.asList("name", "stage_name", "nationality", "tags", "bio"),
                Arrays.asList("name", "stage_name", "bio")));
    }

    @Operation(summary = "搜索资讯")
    @GetMapping("/article")
    public Result<Map<String, Object>> searchArticle(
            @RequestParam String kw,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) throws Exception {
        saveHistory(kw);
        return Result.success(search(EsIndices.ARTICLE, kw, page, size,
                Arrays.asList("title", "summary", "content", "author"),
                Arrays.asList("title", "summary")));
    }

    @Operation(summary = "聚合搜索(每类 Top 5)")
    @GetMapping("/all")
    public Result<Map<String, Object>> searchAll(@RequestParam String kw) throws Exception {
        saveHistory(kw);
        Map<String, Object> r = new HashMap<>();
        r.put("show", search(EsIndices.SHOW, kw, 1, 5,
                Arrays.asList("name", "description", "venue", "category_name", "city_name"),
                Arrays.asList("name", "description", "venue")));
        r.put("artist", search(EsIndices.ARTIST, kw, 1, 5,
                Arrays.asList("name", "stage_name", "tags", "bio"),
                Arrays.asList("name", "stage_name", "bio")));
        r.put("article", search(EsIndices.ARTICLE, kw, 1, 5,
                Arrays.asList("title", "summary", "content"),
                Arrays.asList("title", "summary")));
        return Result.success(r);
    }

    @Operation(summary = "搜索历史")
    @GetMapping("/history")
    public Result<List<String>> history() {
        String key = historyKey();
        if (key == null) return Result.success(Collections.emptyList());
        List<String> list = redis.opsForList().range(key, 0, HISTORY_MAX - 1);
        return Result.success(list == null ? Collections.emptyList() : list);
    }

    @Operation(summary = "清空搜索历史")
    @DeleteMapping("/history")
    public Result<Void> clearHistory() {
        String key = historyKey();
        if (key != null) redis.delete(key);
        return Result.success(null);
    }

    /** 保存搜索历史：去重 + 头插 + 截断 + 7 天过期 */
    private void saveHistory(String kw) {
        if (kw == null || kw.trim().isEmpty()) return;
        String key = historyKey();
        if (key == null) return;
        String value = kw.trim();
        redis.opsForList().remove(key, 0, value);
        redis.opsForList().leftPush(key, value);
        redis.opsForList().trim(key, 0, HISTORY_MAX - 1);
        redis.expire(key, Duration.ofDays(7));
    }

    /** 当前登录用户的历史 key；未登录返回 null */
    private String historyKey() {
        try {
            Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
            if (principal instanceof Long) return "search:history:" + principal;
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 通用 ES 查询：multi_match + 高亮 + 分页 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Map<String, Object> search(String index, String kw, int page, int size,
                                       List<String> searchFields, List<String> highlightFields) throws Exception {
        // 构造 query：空 kw → match_all，否则 multi_match best_fields
        Query query;
        if (kw == null || kw.trim().isEmpty()) {
            query = Query.of(q -> q.matchAll(m -> m));
        } else {
            query = MultiMatchQuery.of(m -> m
                    .query(kw)
                    .fields(searchFields)
                    .type(TextQueryType.BestFields)
            )._toQuery();
        }

        // 构造高亮
        SearchRequest.Builder reqBuilder = new SearchRequest.Builder()
                .index(index)
                .query(query)
                .from((page - 1) * size)
                .size(size);

        if (highlightFields != null && !highlightFields.isEmpty()) {
            Map<String, HighlightField> hfMap = new LinkedHashMap<>();
            for (String f : highlightFields) {
                hfMap.put(f, HighlightField.of(h -> h));
            }
            reqBuilder.highlight(Highlight.of(h -> h
                    .preTags("<em>")
                    .postTags("</em>")
                    .fields(hfMap)));
        }

        SearchResponse<Map> resp = esClient.search(reqBuilder.build(), Map.class);

        List<Map<String, Object>> items = new ArrayList<>();
        for (Hit<Map> hit : resp.hits().hits()) {
            Map<String, Object> src = hit.source() != null
                    ? new HashMap<>((Map<String, Object>) hit.source())
                    : new HashMap<>();
            // 合并高亮字段到 _highlight，前端按字段名渲染
            Map<String, List<String>> hl = hit.highlight();
            if (hl != null && !hl.isEmpty()) {
                Map<String, String> highlight = new HashMap<>();
                hl.forEach((k, fragments) -> {
                    if (fragments == null || fragments.isEmpty()) return;
                    highlight.put(k, String.join("", fragments));
                });
                src.put("_highlight", highlight);
            }
            items.add(src);
        }
        Map<String, Object> r = new HashMap<>();
        r.put("list", items);
        r.put("total", resp.hits().total() != null ? resp.hits().total().value() : 0);
        r.put("page", page);
        r.put("size", size);
        return r;
    }
}
