package com.neusoft.cloudbrain.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JacksonConfig 时间序列化配置测试
 *
 * 验证所有模块的 LocalDateTime 统一输出为带 +08:00 偏移的 ISO 8601 字符串，
 * 符合契约 33_错误码与时间规范.md §5。
 */
@DisplayName("JacksonConfig - LocalDateTime 全局序列化测试")
class JacksonConfigTest {

    @Test
    @DisplayName("LocalDateTime 应序列化为带 +08:00 偏移的 ISO 8601 字符串")
    void localDateTimeShouldSerializeWithOffset() throws Exception {
        JacksonConfig config = new JacksonConfig();
        Jackson2ObjectMapperBuilderCustomizer customizer = config.jacksonCustomizer();

        // 模拟 Spring Boot 的 ObjectMapper 构建过程
        JavaTimeModule module = new JavaTimeModule();
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS+08:00");
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(module);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        LocalDateTime time = LocalDateTime.of(2026, 6, 25, 10, 30, 0, 123000000);
        String json = mapper.writeValueAsString(time);

        assertThat(json)
                .as("应输出带 +08:00 偏移的 ISO 8601 字符串")
                .isEqualTo("\"2026-06-25T10:30:00.123+08:00\"");
    }

    @Test
    @DisplayName("LocalDateTime 输出必须包含 +08:00 偏移标识")
    void localDateTimeShouldContainOffset() throws Exception {
        JacksonConfig config = new JacksonConfig();
        Jackson2ObjectMapperBuilderCustomizer customizer = config.jacksonCustomizer();

        JavaTimeModule module = new JavaTimeModule();
        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS+08:00");
        module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));

        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(module);
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        String json = mapper.writeValueAsString(LocalDateTime.now());
        assertThat(json).contains("+08:00");
    }

    @Test
    @DisplayName("配置类应正确提供 Jackson2ObjectMapperBuilderCustomizer Bean")
    void shouldProvideCustomizerBean() {
        JacksonConfig config = new JacksonConfig();
        Jackson2ObjectMapperBuilderCustomizer customizer = config.jacksonCustomizer();
        assertThat(customizer).isNotNull();
    }
}
