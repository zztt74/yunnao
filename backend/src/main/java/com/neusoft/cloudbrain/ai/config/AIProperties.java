package com.neusoft.cloudbrain.ai.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 能力配置属性
 *
 * 对应配置文件 application-ai.yml 中 app.ai 前缀。
 *
 * 规则（来自 13_AI能力集成AI任务书.md 第4节、32_AI能力契约规范.md 第4节）：
 * - mode 决定使用 Mock 还是真实 Provider
 * - timeout-ms 为单次 Provider 请求超时
 * - max-retries 为重试次数（仅对超时/5xx）
 * - 不配置真实 AI Key 时默认 Mock
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai")
public class AIProperties {

    /**
     * AI 模式：MOCK 或 HTTP
     */
    private Mode mode = Mode.MOCK;

    /**
     * 单次 Provider 请求超时（毫秒）
     */
    private long timeoutMs = 8000;

    /**
     * 最大重试次数（仅对超时/5xx），0 表示不重试
     */
    private int maxRetries = 1;

    /**
     * 真实 HTTP Provider 配置
     */
    private Http http = new Http();

    /**
     * Mock Provider 场景触发关键词配置
     */
    private Mock mock = new Mock();

    public enum Mode {
        MOCK, HTTP
    }

    @Getter
    @Setter
    public static class Http {
        /**
         * AI API 地址
         */
        private String apiUrl;

        /**
         * AI API Key（来自环境变量，不写入日志）
         */
        private String apiKey;

        /**
         * 模型标识
         */
        private String model = "default";
    }

    @Getter
    @Setter
    public static class Mock {
        /**
         * 触发超时场景的关键词
         */
        private String timeoutKeyword = "MOCK_TIMEOUT";

        /**
         * 触发非法 JSON 场景的关键词
         */
        private String invalidJsonKeyword = "MOCK_INVALID_JSON";

        /**
         * 触发不存在科室场景的关键词
         */
        private String notExistDeptKeyword = "MOCK_NOT_EXIST_DEPT";

        /**
         * 触发 Provider 异常场景的关键词
         */
        private String providerErrorKeyword = "MOCK_PROVIDER_ERROR";

        /**
         * 触发空结果场景的关键词
         */
        private String emptyKeyword = "MOCK_EMPTY";

        /**
         * 触发高风险结果场景的关键词
         */
        private String highRiskKeyword = "MOCK_HIGH_RISK";
    }
}
