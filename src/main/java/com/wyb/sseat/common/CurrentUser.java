package com.wyb.sseat.common;

import com.wyb.sseat.entity.User;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 获取"当前登录用户"的小工具：
 * JwtAuthenticationFilter 验证令牌成功后，把 User 对象放进了安全上下文，
 * 任何 Service/Controller 调 CurrentUser.get() 就能拿到，不用层层传参。
 */
public final class CurrentUser {

    private CurrentUser() {}

    /** 当前登录用户实体；未登录抛异常（安全配置保证了业务接口必须有令牌才会进来） */
    public static User get() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof User u) {
            return u;
        }
        throw new BizException("请先登录");
    }

    /** 是否已登录（用于"未登录可浏览、登录才有个性化状态"的接口） */
    public static boolean present() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof User;
    }

    public static Long id() {
        return get().getId();
    }
}
