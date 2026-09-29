package com.wyb.sseat.service;

import com.wyb.sseat.common.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 登录安全：同一账号连续输错密码 5 次，锁定 10 分钟，防止暴力猜密码。
 *
 * 设计说明（论文/答辩可讲）：
 * - 计数存 Redis：key=login:fail:{账号}，INCR 原子自增 + EXPIRE 10分钟窗口，
 *   天然解决"重启清零、多实例不共享"两个内存方案的老问题；
 * - INCR 是单命令原子操作，并发登录也不会把计数加丢；
 * - 对外仍只暴露 checkLock/recordFailure/reset 三个方法，调用方零感知（封装易变点）；
 * - 容错：Redis 不可用时降级放行（只记日志），登录主流程不因缓存故障被阻断。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginSecurityService {

    /** 允许的连续失败次数 */
    private static final int MAX_FAIL = 5;
    /** 失败计数窗口/锁定时长（分钟） */
    private static final long LOCK_MINUTES = 10;
    /** Redis key 前缀 */
    private static final String KEY_PREFIX = "login:fail:";

    private final StringRedisTemplate redis;

    /** 登录前调用：仍在锁定期内则直接拒绝 */
    public void checkLock(String username) {
        String count = safeGet(KEY_PREFIX + username);
        if (count != null && Integer.parseInt(count) >= MAX_FAIL) {
            Long ttl = null;
            try { ttl = redis.getExpire(KEY_PREFIX + username, TimeUnit.SECONDS); } catch (Exception ignored) {}
            long remainMin = (ttl == null || ttl <= 0) ? 1 : (ttl + 59) / 60;
            throw new BizException("密码连续输错次数过多，账号已锁定，请 " + remainMin + " 分钟后再试");
        }
    }

    /** 密码校验失败后调用：累计一次，返回"还剩几次被锁"的提示文字 */
    public String recordFailure(String username) {
        try {
            Long fails = redis.opsForValue().increment(KEY_PREFIX + username);
            if (fails != null && fails == 1L) {
                // 首次失败时设置窗口期；后续 INCR 不重置过期时间
                redis.expire(KEY_PREFIX + username, Duration.ofMinutes(LOCK_MINUTES));
            }
            if (fails != null && fails >= MAX_FAIL) {
                return "，已连续输错 " + MAX_FAIL + " 次，账号锁定 " + LOCK_MINUTES + " 分钟";
            }
            long remain = MAX_FAIL - (fails == null ? 1 : fails);
            return "，再输错 " + remain + " 次将锁定账号 " + LOCK_MINUTES + " 分钟";
        } catch (Exception e) {
            log.warn("Redis 不可用，登录失败计数降级: {}", e.getMessage());
            return "";
        }
    }

    /** 登录成功后调用：清零失败计数 */
    public void reset(String username) {
        try {
            redis.delete(KEY_PREFIX + username);
        } catch (Exception e) {
            log.warn("Redis 不可用，登录计数清除跳过: {}", e.getMessage());
        }
    }

    private String safeGet(String key) {
        try {
            return redis.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis 不可用，锁定检查跳过: {}", e.getMessage());
            return null;
        }
    }
}
