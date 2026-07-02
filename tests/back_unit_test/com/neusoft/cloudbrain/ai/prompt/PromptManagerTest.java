package com.neusoft.cloudbrain.ai.prompt;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PromptManager 单元测试（B6）
 *
 * 覆盖任务书 B6 验收要求：单元测试覆盖 Prompt 选择和回退。
 * - 通用 Prompt 加载与获取
 * - 科室专用 Prompt 选择（内科/儿科）
 * - 大小写归一化匹配
 * - 找不到科室模板时回退到通用
 * - departmentCode 为 null 时回退到通用
 *
 * 测试基于真实 classpath:prompts 文件加载，验证 loadPrompts + 选择 + 回退完整链路。
 */
@DisplayName("PromptManager - Prompt 选择与回退测试")
class PromptManagerTest {

    private static PromptManager promptManager;

    @BeforeAll
    static void setUp() {
        promptManager = new PromptManager();
        promptManager.loadPrompts();
    }

    @Test
    @DisplayName("通用 Prompt 加载成功且可获取")
    void getPrompt_generic_loaded() {
        String content = promptManager.getPrompt("medical_record");
        assertThat(content).isNotNull();
        assertThat(content).contains("病历书写辅助助手");
    }

    @Test
    @DisplayName("通用 Prompt 版本为 v1")
    void getPromptVersion_generic_returnsV1() {
        assertThat(promptManager.getPromptVersion("medical_record")).isEqualTo("v1");
    }

    @Test
    @DisplayName("内科专用 Prompt 被选中且与通用不同")
    void getPrompt_internal_returnsDeptSpecific() {
        String generic = promptManager.getPrompt("medical_record");
        String internal = promptManager.getPrompt("medical_record", "dept_internal");

        assertThat(internal).isNotNull();
        assertThat(internal).contains("内科");
        assertThat(internal).isNotEqualTo(generic);
    }

    @Test
    @DisplayName("儿科专用 Prompt 被选中且与通用不同")
    void getPrompt_pediatrics_returnsDeptSpecific() {
        String generic = promptManager.getPrompt("medical_record");
        String pediatrics = promptManager.getPrompt("medical_record", "dept_pediatrics");

        assertThat(pediatrics).isNotNull();
        assertThat(pediatrics).contains("儿科");
        assertThat(pediatrics).isNotEqualTo(generic);
    }

    @Test
    @DisplayName("大写科室 code 归一化为小写后匹配")
    void getPrompt_upperCaseCode_normalizedToLowerCase() {
        String lower = promptManager.getPrompt("medical_record", "dept_internal");
        String upper = promptManager.getPrompt("medical_record", "DEPT_INTERNAL");

        assertThat(upper).isEqualTo(lower);
    }

    @Test
    @DisplayName("未知科室 code 回退到通用 Prompt")
    void getPrompt_unknownDept_fallsBackToGeneric() {
        String generic = promptManager.getPrompt("medical_record");
        String fallback = promptManager.getPrompt("medical_record", "dept_unknown");

        assertThat(fallback).isEqualTo(generic);
    }

    @Test
    @DisplayName("departmentCode 为 null 回退到通用 Prompt")
    void getPrompt_nullDeptCode_fallsBackToGeneric() {
        String generic = promptManager.getPrompt("medical_record");
        String fallback = promptManager.getPrompt("medical_record", null);

        assertThat(fallback).isEqualTo(generic);
    }

    @Test
    @DisplayName("空白 departmentCode 回退到通用 Prompt")
    void getPrompt_blankDeptCode_fallsBackToGeneric() {
        String generic = promptManager.getPrompt("medical_record");
        String fallback = promptManager.getPrompt("medical_record", "  ");

        assertThat(fallback).isEqualTo(generic);
    }

    @Test
    @DisplayName("未知科室版本回退到通用版本")
    void getPromptVersion_unknownDept_fallsBackToGeneric() {
        assertThat(promptManager.getPromptVersion("medical_record", "dept_unknown"))
                .isEqualTo(promptManager.getPromptVersion("medical_record"));
    }

    @Test
    @DisplayName("内科专用 Prompt 版本为 v1")
    void getPromptVersion_internal_returnsV1() {
        assertThat(promptManager.getPromptVersion("medical_record", "dept_internal"))
                .isEqualTo("v1");
    }
}
