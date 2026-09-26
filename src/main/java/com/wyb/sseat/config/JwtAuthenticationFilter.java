package com.wyb.sseat.config;

import com.wyb.sseat.common.JwtUtil;
import com.wyb.sseat.entity.Role;
import com.wyb.sseat.entity.User;
import com.wyb.sseat.mapper.RoleMapper;
import com.wyb.sseat.mapper.UserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器：每个请求进来时先经过这里。
 * 流程：取请求头 Authorization 里的令牌 → 解析出用户 id → 查库确认
 *       用户存在且未被禁用、角色未被禁用 → 把用户放入"安全上下文"。
 * 关键点：每次请求都查库校验状态，这样管理员禁用用户后，
 *         该用户手里的旧令牌会立即失效（而不是等到过期）。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        // 认证方案名按 HTTP 标准(RFC 7235)不区分大小写：前端发 "Bearer xxx"、
        // Knife4j 文档页自动填 "bearer xxx"，都要能认出，故用 equalsIgnoreCase
        if (header != null && header.length() > 7 && header.substring(0, 7).equalsIgnoreCase("Bearer ")) {
            Long userId = jwtUtil.parseUserId(header.substring(7).trim());
            if (userId != null) {
                User user = userMapper.selectById(userId);
                if (user != null && user.getStatus() == 1) {
                    Role role = roleMapper.selectOne(
                            new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, user.getRoleCode()));
                    if (role != null && role.getStatus() == 1) {
                        // ROLE_ 前缀是 Spring Security 约定：hasRole("admin") 匹配 ROLE_admin
                        List<SimpleGrantedAuthority> authorities =
                                List.of(new SimpleGrantedAuthority("ROLE_" + role.getRoleCode()));
                        var auth = new UsernamePasswordAuthenticationToken(user, null, authorities);
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                }
            }
        }
        chain.doFilter(request, response);
    }
}
