package com.neusoft.cloudbrain.auth.security;

import com.neusoft.cloudbrain.auth.dto.AuthPrincipal;
import com.neusoft.cloudbrain.auth.service.JwtService;
import com.neusoft.cloudbrain.auth.repository.UserAccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * JWT 认证过滤器
 *
 * 从请求头提取 Bearer Token 并设置 Security Context
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserAccountRepository userAccountRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);

        if (StringUtils.hasText(token)) {
            try {
                // 解析 Token
                var claims = jwtService.parseToken(token);

                // 获取用户 ID
                Long userId = Long.parseLong(claims.getSubject());

                // 查询用户最新状态
                userAccountRepository.findById(userId).ifPresent(user -> {
                    // 校验 tokenVersion
                    Object tokenVersion = claims.get("tokenVersion");
                    if (tokenVersion != null && user.getTokenVersion().equals(((Number) tokenVersion).longValue())) {
                        // 校验账号状态
                        if (user.getEnabled() && user.getAccountNonLocked()) {
                            // 构建 Authentication
                            List<String> roles = claims.get("roles", List.class);
                            var authorities = roles.stream()
                                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                                    .collect(Collectors.toList());

                            AuthPrincipal principal = new AuthPrincipal(
                                    userId,
                                    user.getUsername(),
                                    Set.copyOf(roles),
                                    user.getTokenVersion()
                            );

                            var authentication = new UsernamePasswordAuthenticationToken(
                                    principal, null, authorities
                            );

                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        }
                    }
                });
            } catch (Exception e) {
                // Token 无效，不设置认证信息
                logger.debug("JWT 认证失败: " + e.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头提取 Bearer Token
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
