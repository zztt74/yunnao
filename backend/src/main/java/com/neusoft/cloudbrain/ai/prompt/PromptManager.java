package com.neusoft.cloudbrain.ai.prompt;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
                PromptEntry existing = promptCache.get(parsed.capability);
                if (existing == null || parsed.version > existing.version) {
                    promptCache.put(parsed.capability,
                            new PromptEntry(content, parsed.version, parsed.versionString));
                }
            }
            log.info("已加载 {} 个 system prompt: {}", promptCache.size(), promptCache.keySet());
        } catch (IOException e) {
            log.error("加载 system prompt 失败", e);
        }
    }

    /**
     * 获取指定 capability 的 prompt 内容
     */
    public String getPrompt(String capability) {
        PromptEntry entry = promptCache.get(capability);
        return entry != null ? entry.content() : null;
    }

    /**
     * 获取指定 capability 的 prompt 版本（如 "v1"）
     */
    public String getPromptVersion(String capability) {
        PromptEntry entry = promptCache.get(capability);
        return entry != null ? entry.versionString() : null;
    }

    /**
     * 解析文件名 {capability}_v{version}.txt
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
        String capability = base.substring(0, vIndex);
        String versionStr = base.substring(vIndex + 2);
        try {
            int version = Integer.parseInt(versionStr);
            return new ParsedName(capability, version, "v" + version);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record ParsedName(String capability, int version, String versionString) {
    }

    private record PromptEntry(String content, int version, String versionString) {
    }
}
