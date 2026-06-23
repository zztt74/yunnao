package com.neusoft.cloudbrain.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LoginRateLimiter 单元测试
 *
 * 测试场景（来自 41_质量测试与完成定义.md 11.1）：
 * - 登录限流：5 分钟内最多 10 次失败
 */
@DisplayName("LoginRateLimiter - 登录限流测试")
class LoginRateLimiterTest {

    private LoginRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new LoginRateLimiter();
    }

    @Test
    @DisplayName("首次请求应该允许")
    void isAllowed_firstRequest_shouldReturnTrue() {
        assertTrue(rateLimiter.isAllowed("192.168.1.1", "testuser"));
    }

    @Test
    @DisplayName("未超过阈值时应该允许")
    void isAllowed_underThreshold_shouldReturnTrue() {
        String ip = "192.168.1.1";
        String username = "testuser";

        // 记录 9 次失败（阈值是 10）
        for (int i = 0; i < 9; i++) {
            rateLimiter.recordFailure(ip, username);
        }

        assertTrue(rateLimiter.isAllowed(ip, username));
    }

    @Test
    @DisplayName("达到阈值后应该拒绝")
    void isAllowed_atThreshold_shouldReturnFalse() {
        String ip = "192.168.1.1";
        String username = "testuser";

        // 记录 10 次失败（达到阈值）
        for (int i = 0; i < 10; i++) {
            rateLimiter.recordFailure(ip, username);
        }

        assertFalse(rateLimiter.isAllowed(ip, username));
    }

    @Test
    @DisplayName("不同 IP 应该独立计数")
    void isAllowed_differentIp_shouldCountIndependently() {
        String ip1 = "192.168.1.1";
        String ip2 = "192.168.1.2";
        String username = "testuser";

        // IP1 达到阈值
        for (int i = 0; i < 10; i++) {
            rateLimiter.recordFailure(ip1, username);
        }
        assertFalse(rateLimiter.isAllowed(ip1, username));

        // IP2 仍然允许
        assertTrue(rateLimiter.isAllowed(ip2, username));
    }

    @Test
    @DisplayName("不同用户名应该独立计数")
    void isAllowed_differentUser_shouldCountIndependently() {
        String ip = "192.168.1.1";
        String user1 = "testuser1";
        String user2 = "testuser2";

        // user1 达到阈值
        for (int i = 0; i < 10; i++) {
            rateLimiter.recordFailure(ip, user1);
        }
        assertFalse(rateLimiter.isAllowed(ip, user1));

        // user2 仍然允许
        assertTrue(rateLimiter.isAllowed(ip, user2));
    }

    @Test
    @DisplayName("登录成功后重置限流记录")
    void reset_afterSuccessfulLogin_shouldClearRecord() {
        String ip = "192.168.1.1";
        String username = "testuser";

        // 记录 5 次失败
        for (int i = 0; i < 5; i++) {
            rateLimiter.recordFailure(ip, username);
        }

        // 模拟登录成功
        rateLimiter.reset(ip, username);

        // 重置后应该允许
        assertTrue(rateLimiter.isAllowed(ip, username));
    }

    @Test
    @DisplayName("清理过期记录")
    void cleanup_expiredRecords_shouldRemove() {
        String ip = "192.168.1.1";
        String username = "testuser";

        rateLimiter.recordFailure(ip, username);

        // 清理（虽然还没过期，但方法应该可调用）
        assertDoesNotThrow(() -> rateLimiter.cleanup());
    }
}
