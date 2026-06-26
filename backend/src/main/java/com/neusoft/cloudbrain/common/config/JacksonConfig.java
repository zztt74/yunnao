package com.neusoft.cloudbrain.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Jackson 全局时间序列化配置
 *
 * 确保所有模块的 LocalDateTime 字段统一输出为带 +08:00 偏移的 ISO 8601 字符串，
 * 符合契约 30_接口数据与错误契约.md §5、33_错误码与时间规范.md §5。
 *
 * 说明：
 * - 数据库列类型为 DATETIME(无时区)，JPA 映射为 LocalDateTime；
 * - 通过在序列化时附加 +08:00 偏移，使 API 输出统一为带时区的 ISO 8601；
 * - 这样无需改动各模块 Entity/DTO 的 LocalDateTime 字段类型，
 *   避免大规模重构引入风险。
 */
@Configuration
public class JacksonConfig {

    /** Asia/Shanghai 时区偏移标识 */
    private static final String OFFSET = "+08:00";

    /** LocalDateTime 输出格式：yyyy-MM-dd'T'HH:mm:ss.SSS+08:00 */
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS" + OFFSET);

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> {
            JavaTimeModule module = new JavaTimeModule();
            module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(FORMATTER));
            builder.modules(module);
            builder.featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        };
    }
}
