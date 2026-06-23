package com.neusoft.cloudbrain.auth.service;

import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import java.util.UUID;

/**
 * JWT 服务
 *
 * 安全基线：
 * - BCrypt cost=12（在 AuthService 中处理）
 * - Access Token 有效期 2 小时
 * - 无 Refresh Token
 * - JWT Claims：sub, roles, tokenVersion, iat, exp, jti
 */
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:7200}")
    private long jwtExpirationInSeconds;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 JWT Token
     */
    public String generateToken(AuthPrincipal principal) {
        Instant now = Instant.now();
        Instant expiration = now.plusSeconds(jwtExpirationInSeconds);

        return Jwts.builder()
                .subject(String.valueOf(principal.userId()))
                .claim("roles", principal.roles())
                .claim("tokenVersion", principal.tokenVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .id(UUID.randomUUID().toString())
                .signWith(getSigningKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 解析 Token 并返回 Claims
     */
    public Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            throw new JwtException("Token 已过期", e);
        } catch (JwtException e) {
            throw new JwtException("Token 格式错误或签名无效", e);
        }
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token, AuthPrincipal principal) {
        try {
            Claims claims = parseToken(token);

            // 校验用户 ID
            if (!String.valueOf(principal.userId()).equals(claims.getSubject())) {
                return false;
            }

            // 校验 tokenVersion
            Object tokenVersion = claims.get("tokenVersion");
            if (tokenVersion == null || !principal.tokenVersion().equals(((Number) tokenVersion).longValue())) {
                return false;
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取 Token 过期时间（秒）
     */
    public long getExpirationInSeconds() {
        return jwtExpirationInSeconds;
    }
}
