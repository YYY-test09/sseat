package com.wyb.sseat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyb.sseat.common.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/**
 * Spring Security 核心配置。
 * 规则一：/api/auth/** 和 /uploads/** 任何人可访问（登录、验证码、看图片）；
 * 规则二：/api/admin/** 只允许 ROLE_admin（需求：普通用户无法使用后台接口）；
 * 规则三：其余接口登录即可访问；
 * 规则四：未登录返回 401 JSON、无权限返回 403 JSON，而不是 Spring 默认的 HTML 页面。
 * 前后端分离不使用 Session/Cookie（STATELESS），身份全靠 JWT 请求头。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // 开启后可在方法上用 @PreAuthorize 做更细的权限控制
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())          // JWT 方案不需要 CSRF 防护
            .cors(cors -> cors.configurationSource(corsSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 只放行确实不需要登录的接口，/api/auth/me 等其他接口仍需令牌
                .requestMatchers("/api/auth/captcha", "/api/auth/login", "/api/auth/register",
                                 "/api/auth/sms/send", "/api/auth/sms-register", "/api/auth/sms-login",
                                 "/api/auth/forgot", "/uploads/**", "/error").permitAll()
                // 接口文档相关路径放行（Knife4j 的 doc.html 页面与 springdoc 的 JSON 描述）
                .requestMatchers("/doc.html", "/webjars/**", "/v3/api-docs", "/v3/api-docs/**",
                                 "/swagger-ui/**", "/swagger-ui.html", "/favicon.ico").permitAll()
                // 需求：未登录用户可浏览自习室与资讯、论坛（交互类接口仍需登录）
                .requestMatchers(HttpMethod.GET, "/api/rooms/**", "/api/news/**", "/api/posts/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("admin")
                .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> writeJson(res, 401, "未登录或登录已过期"))
                .accessDeniedHandler((req, res, e) -> writeJson(res, 403, "没有权限执行此操作")))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 开发期前端走 Vite 代理其实用不到 CORS；这里保留兜底，方便以后独立部署 */
    private CorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("*"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    /** 手动往响应里写 JSON 错误信息 */
    private void writeJson(HttpServletResponse res, int code, String msg) throws IOException {
        res.setStatus(code);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write(objectMapper.writeValueAsString(Result.error(code, msg)));
    }
}
