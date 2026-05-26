package com.ticket.common.es.index;

/**
 * 演出索引 mapping。
 * 字段说明：
 *  - id              : long      演出ID
 *  - name            : text+kw   名称(可搜可聚合)
 *  - description     : text      简介
 *  - venue           : text      场馆
 *  - category_id     : long
 *  - category_name   : keyword
 *  - city_id         : long
 *  - city_name       : keyword
 *  - poster_url      : keyword   不分词
 *  - status          : integer
 *  - create_time     : date
 *  - avg_rating      : float
 *  - review_count    : integer
 */
public final class ShowIndexMapping {

    private ShowIndexMapping() {}

    public static final String JSON = "{\n" +
            "  \"settings\": { \"number_of_shards\": 1, \"number_of_replicas\": 0 },\n" +
            "  \"mappings\": {\n" +
            "    \"properties\": {\n" +
            "      \"id\":             { \"type\": \"long\" },\n" +
            "      \"name\":           { \"type\": \"text\", \"fields\": { \"kw\": { \"type\": \"keyword\" } } },\n" +
            "      \"description\":    { \"type\": \"text\" },\n" +
            "      \"venue\":          { \"type\": \"text\" },\n" +
            "      \"category_id\":    { \"type\": \"long\" },\n" +
            "      \"category_name\":  { \"type\": \"keyword\" },\n" +
            "      \"city_id\":        { \"type\": \"long\" },\n" +
            "      \"city_name\":      { \"type\": \"keyword\" },\n" +
            "      \"poster_url\":     { \"type\": \"keyword\", \"index\": false },\n" +
            "      \"status\":         { \"type\": \"integer\" },\n" +
            "      \"create_time\":    { \"type\": \"date\", \"format\": \"yyyy-MM-dd HH:mm:ss||epoch_millis\" },\n" +
            "      \"avg_rating\":     { \"type\": \"float\" },\n" +
            "      \"review_count\":   { \"type\": \"integer\" }\n" +
            "    }\n" +
            "  }\n" +
            "}";
}
