package com.neusoft.cloudbrain.auth.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 登录限流服务
 *
 * 安全机制：
 * - IP + 用户名维度双重限流
 * - 默认 5 分钟内最多 10 次失败
 */
@Service
public class LoginRateLimiter {

    /** 最大失败次数（5 分钟窗口） */
    private static final int MAX_ATTEMPTS = 10;

    /** 窗口时间（毫秒）= 5 分钟 */
    private static final long WINDOW_MS = 5 * 60 * 1000L;

    /** 记录格式：key = "ip:username", value = 失败次数和首次失败时间 */
    private final ConcurrentMap<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();

    /**
     * 检查是否允许登录尝试（只检查不计数）
     *
     * @param ip       客户端 IP
     * @param username 用户名
     * @return true-允许，false-被限流
     */
    public boolean isAllowed(String ip, String username) {
        String key = buildKey(ip, username);
        RateLimitEntry entry = rateLimitMap.get(key);

        if (entry == null) {
            return true;
        }

        long now = System.currentTimeMillis();
        // 窗口已过期
        if (now - entry.firstAttemptTime > WINDOW_MS) {
            return true;
        }

        return entry.attempts < MAX_ATTEMPTS;
    }

    /**
     * 记录一次登录失败
     */
    public void recordFailure(String ip, String username) {
        String key = buildKey(ip, username);
        long now = System.currentTimeMillis();

        rateLimitMap.compute(key, (k, existing) -> {
            if (existing == null || now - existing.firstAttemptTime > WINDOW_MS) {
                // 新窗口或窗口已过期
                return new RateLimitEntry(1, now);
            }
            // 增加失败计数
            return new RateLimitEntry(existing.attempts + 1, existing.firstAttemptTime);
        });
    }

    /**
     * 重置指定 key 的限流记录（登录成功后调用）
     */
    public void reset(String ip, String username) {
        rateLimitMap.remove(buildKey(ip, username));
    }

    /**
     * 清理过期的限流记录（可由定时任务调用）
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        rateLimitMap.entrySet().removeIf(entry ->
                now - entry.getValue().firstAttemptTime > WINDOW_MS
        );
    }

    private String buildKey(String ip, String username) {
        return ip + ":" + username;
    }

    private record RateLimitEntry(int attempts, long firstAttemptTime) {}
}
