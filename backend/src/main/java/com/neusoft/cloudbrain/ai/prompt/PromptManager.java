package com.neusoft.cloudbrain.ai.prompt;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompt 管理器
 *
 * 职责（来自任务 STAGE-AI-1 交付物）：
 * - 从 classpath:prompts/ 加载 system prompt 骨架文件
 * - 文件命名约定：{capability}_v{version}.txt（如 triage_v1.txt）
 * - 提供按 capability 获取 prompt 内容和版本的接口
 * - 启动时加载并缓存，避免运行时 IO
 *
 * 版本管理：
 * - 同一 capability 若存在多个版本文件，取最高版本号
 * - promptVersion 记录到 AIInvocationAttempt 用于版本追踪
 */
@Slf4j
@Component
public class PromptManager {

    private static final String PROMPT_LOCATION = "classpath:prompts/*_v*.txt";

    /**
     * 已知 capability 列表（从长到短匹配，避免 medical_record 被误分割为 medical + record）
     */
    private static final List<String> KNOWN_CAPABILITIES = List.of(
            "medical_record", "prescription_review", "result_interpretation", "diagnosis", "triage");

    private final Map<String, PromptEntry> promptCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void loadPrompts() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(PROMPT_LOCATION);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null) {
                    continue;
                }
                ParsedName parsed = parseFilename(filename);
                if (parsed == null) {
                    log.warn("跳过不符合命名规范的 prompt 文件: {}", filename);
                    continue;
                }
                String content = new String(resource.getInputStream().readAllBytes(),
                        StandardCharsets.UTF_8);
                String key = buildCacheKey(parsed.capability, parsed.departmentCode);
                PromptEntry existing = promptCache.get(key);
                if (existing == null || parsed.version > existing.version()) {
                    promptCache.put(key,
                            new PromptEntry(content, parsed.version, parsed.versionString, parsed.departmentCode));
                }
            }
            log.info("已加载 {} 个 system prompt: {}", promptCache.size(), promptCache.keySet());
        } catch (IOException e) {
            log.error("加载 system prompt 失败", e);
        }
    }

    /**
     * 获取指定 capability 的通用 prompt 内容
     */
    public String getPrompt(String capability) {
        PromptEntry entry = promptCache.get(buildCacheKey(capability, null));
        return entry != null ? entry.content() : null;
    }

    /**
     * 获取指定 capability 的通用 prompt 版本（如 "v1"）
     */
    public String getPromptVersion(String capability) {
        PromptEntry entry = promptCache.get(buildCacheKey(capability, null));
        return entry != null ? entry.versionString() : null;
    }

    /**
     * 按科室获取专用 prompt（B6）
     *
     * 查找顺序：
     * 1. {capability}_{departmentCode小写}_v{version}.txt（科室专用）
     * 2. {capability}_v{version}.txt（通用回退）
     *
     * 找不到科室专用时回退到通用，不因找不到科室模板导致 AI 调用失败。
     */
    public String getPrompt(String capability, String departmentCode) {
        if (departmentCode != null && !departmentCode.isBlank()) {
            String normalizedCode = departmentCode.toLowerCase();
            PromptEntry deptEntry = promptCache.get(buildCacheKey(capability, normalizedCode));
            if (deptEntry != null) {
                return deptEntry.content();
            }
        }
        return getPrompt(capability);
    }

    /**
     * 按科室获取专用 prompt 版本
     */
    public String getPromptVersion(String capability, String departmentCode) {
        if (departmentCode != null && !departmentCode.isBlank()) {
            String normalizedCode = departmentCode.toLowerCase();
            PromptEntry deptEntry = promptCache.get(buildCacheKey(capability, normalizedCode));
            if (deptEntry != null) {
                return deptEntry.versionString();
            }
        }
        return getPromptVersion(capability);
    }

    /**
     * 构建缓存 key
     *
     * 通用 prompt: {capability}
     * 科室专用: {capability}__{departmentCode}
     */
    private String buildCacheKey(String capability, String departmentCode) {
        if (departmentCode == null || departmentCode.isBlank()) {
            return capability;
        }
        return capability + "__" + departmentCode;
    }

    /**
     * 解析文件名
     *
     * 通用：{capability}_v{version}.txt
     * 科室专用：{capability}_{departmentCode}_v{version}.txt
     *
     * 使用已知 capability 列表匹配前缀，避免含下划线的 capability（如 medical_record）被误分割。
     * departmentCode 查找时统一转小写匹配。
     */
    private ParsedName parseFilename(String filename) {
        if (!filename.endsWith(".txt")) {
            return null;
        }
        String base = filename.substring(0, filename.length() - 4);
        int vIndex = base.lastIndexOf("_v");
        if (vIndex < 0) {
            return null;
        }
        String versionStr = base.substring(vIndex + 2);
        try {
            int version = Integer.parseInt(versionStr);
            String prefix = base.substring(0, vIndex);

            for (String cap : KNOWN_CAPABILITIES) {
                if (prefix.equals(cap)) {
                    return new ParsedName(cap, version, "v" + version, null);
                }
                if (prefix.startsWith(cap + "_")) {
                    String deptCode = prefix.substring(cap.length() + 1).toLowerCase();
                    return new ParsedName(cap, version, "v" + version, deptCode);
                }
            }
            log.warn("未识别的 capability 前缀: {}", prefix);
            return null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record ParsedName(String capability, int version, String versionString, String departmentCode) {
    }

    private record PromptEntry(String content, int version, String versionString, String departmentCode) {
    }
}
