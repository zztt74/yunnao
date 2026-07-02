package com.neusoft.cloudbrain.auth.service;

import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtService 单元测试
 *
 * 测试场景（来自 41_质量测试与完成定义.md 11.1）：
 * - Token 生成和解析
 * - Token 过期检测
 * - Token 签名校验
 * - tokenVersion 校验使旧 Token 失效
 */
@DisplayName("JwtService - JWT 服务测试")
class JwtServiceTest {

    private JwtService jwtService;
    private static final String TEST_SECRET = "ThisIsASecretKeyForTestingThatIsAtLeast32BytesLong!!";

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtService, "jwtExpirationInSeconds", 7200L);
    }

    @Test
    @DisplayName("生成有效的 JWT Token")
    void generateToken_validPrincipal_shouldGenerateToken() {
        AuthPrincipal principal = new AuthPrincipal(
                1L,
                "testuser",
                Set.of("ADMIN"),
                0L
        );

        String token = jwtService.generateToken(principal);

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    @DisplayName("解析 Token 返回正确的 Claims")
    void parseToken_validToken_shouldReturnClaims() {
        AuthPrincipal principal = new AuthPrincipal(
                1L,
                "testuser",
                Set.of("ADMIN", "DOCTOR"),
                0L
        );

        String token = jwtService.generateToken(principal);
        var claims = jwtService.parseToken(token);

        assertEquals("1", claims.getSubject());
        // 验证 roles（不保证顺序）
        var actualRoles = claims.get("roles", java.util.List.class);
        assertNotNull(actualRoles);
        assertTrue(actualRoles.contains("ADMIN"));
        assertTrue(actualRoles.contains("DOCTOR"));
        assertEquals(2, actualRoles.size());
        assertEquals(0L, ((Number) claims.get("tokenVersion")).longValue());
        assertNotNull(claims.getId());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("验证有效 Token 应该返回 true")
    void validateToken_validToken_shouldReturnTrue() {
        AuthPrincipal principal = new AuthPrincipal(
                1L,
                "testuser",
                Set.of("ADMIN"),
                0L
        );

        String token = jwtService.generateToken(principal);

        assertTrue(jwtService.validateToken(token, principal));
    }

    @Test
    @DisplayName("tokenVersion 不匹配时 Token 无效")
    void validateToken_tokenVersionMismatch_shouldReturnFalse() {
        AuthPrincipal principalWithVersion0 = new AuthPrincipal(
                1L,
                "testuser",
                Set.of("ADMIN"),
                0L
        );

        AuthPrincipal principalWithVersion1 = new AuthPrincipal(
                1L,
                "testuser",
                Set.of("ADMIN"),
                1L
        );

        // 用 version=0 生成 Token
        String token = jwtService.generateToken(principalWithVersion0);

        // 用 version=1 验证（模拟退出登录后）
        assertFalse(jwtService.validateToken(token, principalWithVersion1));
    }

    @Test
    @DisplayName("用户 ID 不匹配时 Token 无效")
    void validateToken_userIdMismatch_shouldReturnFalse() {
        AuthPrincipal principal1 = new AuthPrincipal(
                1L,
                "testuser",
                Set.of("ADMIN"),
                0L
        );

        AuthPrincipal principal2 = new AuthPrincipal(
                2L,
                "otheruser",
                Set.of("ADMIN"),
                0L
        );

        String token = jwtService.generateToken(principal1);

        assertFalse(jwtService.validateToken(token, principal2));
    }

    @Test
    @DisplayName("篡改的 Token 应该抛出异常")
    void parseToken_tamperedToken_shouldThrowException() {
        String tamperedToken = "valid.header.payload" + ".tampered.signature";

        assertThrows(JwtException.class, () -> jwtService.parseToken(tamperedToken));
    }

    @Test
    @DisplayName("获取过期时间配置值")
    void getExpirationInSeconds_shouldReturnValue() {
        assertEquals(7200L, jwtService.getExpirationInSeconds());
    }

    @Test
    @DisplayName("每次生成的 Token 应该有唯一的 JTI")
    void generateToken_multipleCalls_shouldHaveUniqueJti() {
        AuthPrincipal principal = new AuthPrincipal(
                1L,
                "testuser",
                Set.of("ADMIN"),
                0L
        );

        String token1 = jwtService.generateToken(principal);
        String token2 = jwtService.generateToken(principal);

        var claims1 = jwtService.parseToken(token1);
        var claims2 = jwtService.parseToken(token2);

        assertNotEquals(claims1.getId(), claims2.getId());
    }
}
