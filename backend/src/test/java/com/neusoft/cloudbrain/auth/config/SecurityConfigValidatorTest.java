package com.neusoft.cloudbrain.auth.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SecurityConfigValidator 单元测试
 *
 * 验证 @PostConstruct 启动校验逻辑：
 * - 配置完整时校验通过
 * - 缺少 JWT 密钥、管理员密码或数据库密码时抛出 IllegalStateException
 *
 * 这是修复问题4（从 ApplicationReadyEvent 改为 @PostConstruct）的核心验证。
 * 直接调用 validateSecurityConfig() 方法，避免启动完整 Spring 上下文引入
 * 其他组件（如 AdminDataInitializer）的副作用。
 */
@DisplayName("SecurityConfigValidator - 启动安全配置校验")
class SecurityConfigValidatorTest {

    @Test
    @DisplayName("配置完整时校验应通过")
    void shouldPassWhenConfigComplete() {
        SecurityConfigValidator validator = new SecurityConfigValidator();
        ReflectionTestUtils.setField(validator, "jwtSecret",
                "ThisIsASecretKeyForTestingThatIsAtLeast32BytesLong!!");
        ReflectionTestUtils.setField(validator, "adminPassword", "TestAdmin123!");
        ReflectionTestUtils.setField(validator, "dbPassword", "test-db-password");

        assertThatCode(validator::validateSecurityConfig)
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("JWT 密钥长度不足 32 字节应抛出异常")
    void shouldFailWhenJwtSecretTooShort() {
        SecurityConfigValidator validator = new SecurityConfigValidator();
        ReflectionTestUtils.setField(validator, "jwtSecret", "short");
        ReflectionTestUtils.setField(validator, "adminPassword", "TestAdmin123!");
        ReflectionTestUtils.setField(validator, "dbPassword", "test-db-password");

        assertThatThrownBy(validator::validateSecurityConfig)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT 密钥")
                .hasMessageContaining("32");
    }

    @Test
    @DisplayName("JWT 密钥为空应抛出异常")
    void shouldFailWhenJwtSecretEmpty() {
        SecurityConfigValidator validator = new SecurityConfigValidator();
        ReflectionTestUtils.setField(validator, "jwtSecret", "");
        ReflectionTestUtils.setField(validator, "adminPassword", "TestAdmin123!");
        ReflectionTestUtils.setField(validator, "dbPassword", "test-db-password");

        assertThatThrownBy(validator::validateSecurityConfig)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT 密钥");
    }

    @Test
    @DisplayName("JWT 密钥为 null 应抛出异常")
    void shouldFailWhenJwtSecretNull() {
        SecurityConfigValidator validator = new SecurityConfigValidator();
        ReflectionTestUtils.setField(validator, "jwtSecret", null);
        ReflectionTestUtils.setField(validator, "adminPassword", "TestAdmin123!");
        ReflectionTestUtils.setField(validator, "dbPassword", "test-db-password");

        assertThatThrownBy(validator::validateSecurityConfig)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT 密钥");
    }

    @Test
    @DisplayName("管理员初始密码为空应抛出异常")
    void shouldFailWhenAdminPasswordBlank() {
        SecurityConfigValidator validator = new SecurityConfigValidator();
        ReflectionTestUtils.setField(validator, "jwtSecret",
                "ThisIsASecretKeyForTestingThatIsAtLeast32BytesLong!!");
        ReflectionTestUtils.setField(validator, "adminPassword", "   ");
        ReflectionTestUtils.setField(validator, "dbPassword", "test-db-password");

        assertThatThrownBy(validator::validateSecurityConfig)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("管理员初始密码");
    }

    @Test
    @DisplayName("管理员初始密码为 null 应抛出异常")
    void shouldFailWhenAdminPasswordNull() {
        SecurityConfigValidator validator = new SecurityConfigValidator();
        ReflectionTestUtils.setField(validator, "jwtSecret",
                "ThisIsASecretKeyForTestingThatIsAtLeast32BytesLong!!");
        ReflectionTestUtils.setField(validator, "adminPassword", null);
        ReflectionTestUtils.setField(validator, "dbPassword", "test-db-password");

        assertThatThrownBy(validator::validateSecurityConfig)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("管理员初始密码");
    }

    @Test
    @DisplayName("数据库密码为空应抛出异常")
    void shouldFailWhenDbPasswordBlank() {
        SecurityConfigValidator validator = new SecurityConfigValidator();
        ReflectionTestUtils.setField(validator, "jwtSecret",
                "ThisIsASecretKeyForTestingThatIsAtLeast32BytesLong!!");
        ReflectionTestUtils.setField(validator, "adminPassword", "TestAdmin123!");
        ReflectionTestUtils.setField(validator, "dbPassword", "");

        assertThatThrownBy(validator::validateSecurityConfig)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("数据库密码");
    }

    @Test
    @DisplayName("数据库密码为 null 应抛出异常")
    void shouldFailWhenDbPasswordNull() {
        SecurityConfigValidator validator = new SecurityConfigValidator();
        ReflectionTestUtils.setField(validator, "jwtSecret",
                "ThisIsASecretKeyForTestingThatIsAtLeast32BytesLong!!");
        ReflectionTestUtils.setField(validator, "adminPassword", "TestAdmin123!");
        ReflectionTestUtils.setField(validator, "dbPassword", null);

        assertThatThrownBy(validator::validateSecurityConfig)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("数据库密码");
    }

    @Test
    @DisplayName("JWT 密钥恰好 32 字节应通过（边界值）")
    void shouldPassWhenJwtSecretExactly32Chars() {
        SecurityConfigValidator validator = new SecurityConfigValidator();
        // 恰好 32 个字符
        ReflectionTestUtils.setField(validator, "jwtSecret",
                "01234567890123456789012345678901");
        ReflectionTestUtils.setField(validator, "adminPassword", "TestAdmin123!");
        ReflectionTestUtils.setField(validator, "dbPassword", "test-db-password");

        assertThatCode(validator::validateSecurityConfig)
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("JWT 密钥 31 字节应失败（边界值）")
    void shouldFailWhenJwtSecret31Chars() {
        SecurityConfigValidator validator = new SecurityConfigValidator();
        // 31 个字符
        ReflectionTestUtils.setField(validator, "jwtSecret",
                "0123456789012345678901234567890");
        ReflectionTestUtils.setField(validator, "adminPassword", "TestAdmin123!");
        ReflectionTestUtils.setField(validator, "dbPassword", "test-db-password");

        assertThatThrownBy(validator::validateSecurityConfig)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT 密钥");
    }
}
