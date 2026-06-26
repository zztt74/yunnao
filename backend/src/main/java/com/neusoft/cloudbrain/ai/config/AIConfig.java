package com.neusoft.cloudbrain.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * AI 模块配置类
 *
 * 职责：
 * - 启用 AIProperties 配置绑定
 * - 提供 RestClient Bean（供 HttpLLMProvider 使用，超时由 AIProperties.timeoutMs 控制）
 *
 * 规则（来自 41_质量测试与完成定义.md 第2.2节）：
 * - AI 请求必须设置超时
 * - 重试次数有限
 */
@Configuration
@EnableConfigurationProperties(AIProperties.class)
public class AIConfig {

    /**
     * AI HTTP RestClient
     *
     * 连接超时和读取超时均由 app.ai.timeout-ms 控制（默认 8 秒）。
     * 在 MOCK 模式下不会被使用，但仍创建 Bean 以保持配置一致性。
     */
    @Bean
    public RestClient aiRestClient(AIProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(properties.getTimeoutMs()));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
