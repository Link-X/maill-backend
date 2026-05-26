package com.ticket.common.es.index;

public final class ArtistIndexMapping {

    private ArtistIndexMapping() {}

    public static final String JSON = "{\n" +
            "  \"settings\": { \"number_of_shards\": 1, \"number_of_replicas\": 0 },\n" +
            "  \"mappings\": {\n" +
            "    \"properties\": {\n" +
            "      \"id\":           { \"type\": \"long\" },\n" +
            "      \"name\":         { \"type\": \"text\", \"fields\": { \"kw\": { \"type\": \"keyword\" } } },\n" +
            "      \"stage_name\":   { \"type\": \"text\", \"fields\": { \"kw\": { \"type\": \"keyword\" } } },\n" +
            "      \"avatar_url\":   { \"type\": \"keyword\", \"index\": false },\n" +
            "      \"nationality\":  { \"type\": \"keyword\" },\n" +
            "      \"tags\":         { \"type\": \"keyword\" },\n" +
            "      \"bio\":          { \"type\": \"text\" },\n" +
            "      \"status\":       { \"type\": \"integer\" },\n" +
            "      \"create_time\":  { \"type\": \"date\", \"format\": \"yyyy-MM-dd HH:mm:ss||epoch_millis\" }\n" +
            "    }\n" +
            "  }\n" +
            "}";
}
