package com.ticket.common.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;

/**
 * Jackson 全局配置:
 *  - LocalDateTime 输入接受 "yyyy-MM-dd HH:mm:ss" 与 ISO-8601 "yyyy-MM-ddTHH:mm:ss"
 *  - LocalDateTime / LocalDate 输出统一使用 "yyyy-MM-dd HH:mm:ss" / "yyyy-MM-dd"
 *
 * 解决前端发送 "2026-05-26 11:01:00" 时 Spring 默认只识别 ISO 而抛 HttpMessageNotReadableException 的问题。
 */
@Configuration
public class JacksonDateTimeConfig {

    private static final DateTimeFormatter OUTPUT_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter OUTPUT_DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 输入兼容多种格式;先尝试空格分隔,再尝试 ISO */
    private static final DateTimeFormatter INPUT_DATETIME = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd")
            .optionalStart().appendLiteral(' ').optionalEnd()
            .optionalStart().appendLiteral('T').optionalEnd()
            .appendPattern("HH:mm")
            .optionalStart().appendPattern(":ss").optionalEnd()
            .toFormatter();

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer dateTimeFormatCustomizer() {
        return builder -> {
            SimpleModule module = new SimpleModule();
            module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(INPUT_DATETIME) {
                @Override
                public LocalDateTime deserialize(com.fasterxml.jackson.core.JsonParser parser,
                                                 com.fasterxml.jackson.databind.DeserializationContext ctxt)
                        throws IOException {
                    String text = parser.getValueAsString();
                    if (text == null || text.isEmpty()) return null;
                    try {
                        return LocalDateTime.parse(text, INPUT_DATETIME);
                    } catch (DateTimeParseException e) {
                        // 二次尝试 ISO_LOCAL_DATE_TIME 以兼容更严格的客户端
                        return LocalDateTime.parse(text);
                    }
                }
            });
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(OUTPUT_DATETIME));
            module.addDeserializer(LocalDate.class, new LocalDateDeserializer(DateTimeFormatter.ISO_LOCAL_DATE));
            module.addSerializer(LocalDate.class, new LocalDateSerializer(OUTPUT_DATE));
            builder.modulesToInstall(module);
        };
    }
}
