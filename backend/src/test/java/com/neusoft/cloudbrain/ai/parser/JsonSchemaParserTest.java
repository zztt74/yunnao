package com.neusoft.cloudbrain.ai.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neusoft.cloudbrain.ai.exception.AIInvalidResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JsonSchemaParser 单元测试
 *
 * 覆盖（来自任务 STAGE-AI-1 交付物）：
 * - 剥离 markdown fence
 * - 从文本中提取 JSON 对象
 * - 校验必填字段
 * - 校验枚举值受控
 * - 非法响应映射 AI_INVALID_RESPONSE
 */
@DisplayName("JsonSchemaParser - JSON 解析与校验测试")
class JsonSchemaParserTest {

    private JsonSchemaParser parser;

    @BeforeEach
    void setUp() {
        parser = new JsonSchemaParser(new ObjectMapper());
    }

    // ============================================================
    // 解析测试
    // ============================================================

    @Test
    @DisplayName("解析纯 JSON 文本")
    void parse_plainJson() {
        JsonNode node = parser.parse("{\"name\":\"test\",\"value\":123}");

        assertThat(node.get("name").asText()).isEqualTo("test");
        assertThat(node.get("value").asInt()).isEqualTo(123);
    }

    @Test
    @DisplayName("剥离 markdown json fence")
    void parse_markdownFence() {
        String raw = "```json\n{\"name\":\"test\"}\n```";
        JsonNode node = parser.parse(raw);

        assertThat(node.get("name").asText()).isEqualTo("test");
    }

    @Test
    @DisplayName("剥离 markdown 普通 fence")
    void parse_plainFence() {
        String raw = "```\n{\"name\":\"test\"}\n```";
        JsonNode node = parser.parse(raw);

        assertThat(node.get("name").asText()).isEqualTo("test");
    }

    @Test
    @DisplayName("从包含额外文本的响应中提取 JSON")
    void parse_extractFromText() {
        String raw = "这是 AI 的回复：\n{\"name\":\"test\"}\n以上是结果。";
        JsonNode node = parser.parse(raw);

        assertThat(node.get("name").asText()).isEqualTo("test");
    }

    @Test
    @DisplayName("空响应抛出 AIInvalidResponseException")
    void parse_emptyResponse() {
        assertThatThrownBy(() -> parser.parse(""))
                .isInstanceOf(AIInvalidResponseException.class);
    }

    @Test
    @DisplayName("null 响应抛出 AIInvalidResponseException")
    void parse_nullResponse() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(AIInvalidResponseException.class);
    }

    @Test
    @DisplayName("非 JSON 文本抛出 AIInvalidResponseException")
    void parse_nonJsonText() {
        assertThatThrownBy(() -> parser.parse("这不是一个合法的 JSON 响应"))
                .isInstanceOf(AIInvalidResponseException.class);
    }

    @Test
    @DisplayName("包含大括号但非合法 JSON 抛出 AIInvalidResponseException")
    void parse_malformedJson() {
        assertThatThrownBy(() -> parser.parse("{\"name\": invalid}"))
                .isInstanceOf(AIInvalidResponseException.class);
    }

    // ============================================================
    // 必填字段校验测试
    // ============================================================

    @Test
    @DisplayName("必填字段存在时不抛异常")
    void validateRequired_allPresent() {
        JsonNode node = parser.parse("{\"a\":1,\"b\":\"x\",\"c\":null}");

        parser.validateRequired(node, "a", "b");
        // 不抛异常即通过
    }

    @Test
    @DisplayName("必填字段缺失时抛出异常")
    void validateRequired_missing() {
        JsonNode node = parser.parse("{\"a\":1}");

        assertThatThrownBy(() -> parser.validateRequired(node, "a", "b"))
                .isInstanceOf(AIInvalidResponseException.class)
                .hasMessageContaining("b");
    }

    @Test
    @DisplayName("必填字段为 null 时抛出异常")
    void validateRequired_nullValue() {
        JsonNode node = parser.parse("{\"a\":null}");

        assertThatThrownBy(() -> parser.validateRequired(node, "a"))
                .isInstanceOf(AIInvalidResponseException.class);
    }

    // ============================================================
    // 枚举校验测试
    // ============================================================

    @Test
    @DisplayName("枚举值合法时不抛异常")
    void validateEnum_validValue() {
        JsonNode node = parser.parse("{\"priority\":\"HIGH\"}");

        parser.validateEnum(node, "priority", Set.of("HIGH", "MEDIUM", "LOW"));
    }

    @Test
    @DisplayName("枚举值非法时抛出异常")
    void validateEnum_invalidValue() {
        JsonNode node = parser.parse("{\"priority\":\"URGENT\"}");

        assertThatThrownBy(() -> parser.validateEnum(node, "priority", Set.of("HIGH", "MEDIUM", "LOW")))
                .isInstanceOf(AIInvalidResponseException.class)
                .hasMessageContaining("URGENT");
    }

    @Test
    @DisplayName("枚举字段缺失时不抛异常（可选字段）")
    void validateEnum_missingField() {
        JsonNode node = parser.parse("{\"other\":\"value\"}");

        parser.validateEnum(node, "priority", Set.of("HIGH", "MEDIUM", "LOW"));
    }

    // ============================================================
    // 字符串数组解析测试
    // ============================================================

    @Test
    @DisplayName("解析字符串数组字段")
    void parseStringArray_normalArray() {
        JsonNode node = parser.parse("{\"items\":[\"a\",\"b\",\"c\"]}");

        List<String> items = parser.parseStringArray(node, "items");

        assertThat(items).containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("字符串数组字段缺失时返回空列表")
    void parseStringArray_missingField() {
        JsonNode node = parser.parse("{\"other\":\"value\"}");

        List<String> items = parser.parseStringArray(node, "items");

        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("字符串数组字段为空数组时返回空列表")
    void parseStringArray_emptyArray() {
        JsonNode node = parser.parse("{\"items\":[]}");

        List<String> items = parser.parseStringArray(node, "items");

        assertThat(items).isEmpty();
    }

    @Test
    @DisplayName("字段非数组时返回空列表")
    void parseStringArray_notArray() {
        JsonNode node = parser.parse("{\"items\":\"string\"}");

        List<String> items = parser.parseStringArray(node, "items");

        assertThat(items).isEmpty();
    }
}
