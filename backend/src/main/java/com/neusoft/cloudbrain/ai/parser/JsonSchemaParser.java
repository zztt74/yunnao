package com.neusoft.cloudbrain.ai.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neusoft.cloudbrain.ai.exception.AIInvalidResponseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * JSON Schema 解析器
 *
 * 职责（来自任务 STAGE-AI-1 交付物）：
 * - 剥离 markdown fence（```json ... ```）
 * - 从文本中提取 JSON 对象
 * - 校验必填字段
 * - 校验枚举值受控
 * - 非法响应映射 AI_INVALID_RESPONSE（抛出 AIInvalidResponseException）
 *
 * 设计说明：
 * - 不引入第三方 JSON Schema 校验库（依赖说明待后端角色确认），采用手写校验降级
 * - 使用 Spring 容器中已有的 Jackson ObjectMapper
 */
@Component
@RequiredArgsConstructor
public class JsonSchemaParser {

    private final ObjectMapper objectMapper;

    /**
     * 解析原始文本为 JsonNode
     *
     * 步骤：剥离 markdown fence → 提取 JSON 对象 → Jackson 解析
     *
     * @param raw 模型返回的原始文本
     * @return 解析后的 JsonNode
     * @throws AIInvalidResponseException 如果文本为空、不含合法 JSON 或解析失败
     */
    public JsonNode parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new AIInvalidResponseException("AI 响应为空");
        }
        String stripped = stripMarkdownFence(raw.trim());
        String json = extractJsonObject(stripped);
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new AIInvalidResponseException(
                    "AI 响应非合法 JSON: " + truncate(e.getMessage()), e);
        }
    }

    /**
     * 校验必填字段存在且非 null
     *
     * @param node   已解析的 JSON 节点
     * @param fields 必填字段名列表
     * @throws AIInvalidResponseException 如果任一字段缺失或为 null
     */
    public void validateRequired(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value == null || value.isNull()) {
                throw new AIInvalidResponseException("AI 响应缺少必填字段: " + field);
            }
        }
    }

    /**
     * 校验枚举字段值在受控范围内
     *
     * @param node    已解析的 JSON 节点
     * @param field   字段名
     * @param allowed 允许的枚举值集合
     * @throws AIInvalidResponseException 如果字段值不在允许范围内
     */
    public void validateEnum(JsonNode node, String field, Collection<String> allowed) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return;
        }
        String strValue = value.asText();
        if (!allowed.contains(strValue)) {
            throw new AIInvalidResponseException(
                    "AI 响应字段 " + field + " 枚举值非法: " + strValue
                            + "，允许值: " + allowed);
        }
    }

    /**
     * 将 JSON 数组字段解析为字符串列表
     *
     * @param node  已解析的 JSON 节点
     * @param field 数组字段名
     * @return 字符串列表（字段缺失或非数组时返回空列表）
     */
    public List<String> parseStringArray(JsonNode node, String field) {
        JsonNode arr = node.get(field);
        if (arr == null || !arr.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : arr) {
            result.add(item.asText());
        }
        return result;
    }

    /**
     * 剥离 markdown 代码围栏
     *
     * 支持 ```json ... ``` 和 ``` ... ``` 两种格式
     */
    private String stripMarkdownFence(String text) {
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) {
                String afterFence = text.substring(firstNewline + 1);
                int closingFence = afterFence.lastIndexOf("```");
                if (closingFence >= 0) {
                    return afterFence.substring(0, closingFence).trim();
                }
            }
        }
        return text;
    }

    /**
     * 从文本中提取第一个 JSON 对象（从第一个 '{' 到最后一个 '}'）
     */
    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < 0 || end <= start) {
            throw new AIInvalidResponseException("AI 响应未包含 JSON 对象");
        }
        return text.substring(start, end + 1);
    }

    private String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
