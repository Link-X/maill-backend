package com.ticket.common.es.index;

public final class ArticleIndexMapping {

    private ArticleIndexMapping() {}

    public static final String JSON = "{\n" +
            "  \"settings\": { \"number_of_shards\": 1, \"number_of_replicas\": 0 },\n" +
            "  \"mappings\": {\n" +
            "    \"properties\": {\n" +
            "      \"id\":            { \"type\": \"long\" },\n" +
            "      \"title\":         { \"type\": \"text\", \"fields\": { \"kw\": { \"type\": \"keyword\" } } },\n" +
            "      \"summary\":       { \"type\": \"text\" },\n" +
            "      \"content\":       { \"type\": \"text\" },\n" +
            "      \"cover_url\":     { \"type\": \"keyword\", \"index\": false },\n" +
            "      \"category_id\":   { \"type\": \"long\" },\n" +
            "      \"category_name\": { \"type\": \"keyword\" },\n" +
            "      \"artist_id\":     { \"type\": \"long\" },\n" +
            "      \"author\":        { \"type\": \"keyword\" },\n" +
            "      \"status\":        { \"type\": \"integer\" },\n" +
            "      \"published_at\":  { \"type\": \"date\", \"format\": \"yyyy-MM-dd HH:mm:ss||epoch_millis\" },\n" +
            "      \"create_time\":   { \"type\": \"date\", \"format\": \"yyyy-MM-dd HH:mm:ss||epoch_millis\" }\n" +
            "    }\n" +
            "  }\n" +
            "}";
}
