package com.wyb.sseat.service;

import com.wyb.sseat.common.BizException;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录安全：同一账号连续输错密码 5 次，锁定 10 分钟，防止暴力猜密码。
 *
 * 设计说明（论文/答辩可讲）：
 * - 计数保存在服务器内存（ConcurrentHashMap），与图形验证码存储同理；
 * - 所有调用都通过本类对外方法，将来要换 Redis（INCR + 过期时间）只改本类内部，
 *   其他代码零改动——这叫"把易变点封装在稳定的接口后面"；
 * - 内存方案的已知局限：后端重启后计数清零。单机演示场景可接受，
 *   生产环境应使用 Redis 持久化计数。
 */
@Service
public class LoginSecurityService {

    /** 允许的连续失败次数 */
    private static final int MAX_FAIL = 5;
    /** 超限后的锁定时长（分钟） */
    private static final long LOCK_MINUTES = 10;

    /** 每个账号的失败记录 */
    private static class Attempt {
        int fails;      // 当前连续失败次数
        long lockUntil; // 锁定截止时间戳（0=未锁定）
    }

    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    /** 登录前调用：仍在锁定期内则直接拒绝 */
    public void checkLock(String username) {
        Attempt a = attempts.get(username);
        if (a != null && a.lockUntil > System.currentTimeMillis()) {
            long remainMin = (a.lockUntil - System.currentTimeMillis() + 59_999) / 60_000;
            throw new BizException("密码连续输错次数过多，账号已锁定，请 " + remainMin + " 分钟后再试");
        }
    }

    /** 密码校验失败后调用：累计一次，返回"还剩几次被锁"的提示文字 */
    public String recordFailure(String username) {
        Attempt a = attempts.computeIfAbsent(username, k -> new Attempt());
        a.fails++;
        if (a.fails >= MAX_FAIL) {
            a.lockUntil = System.currentTimeMillis() + LOCK_MINUTES * 60_000;
            a.fails = 0; // 锁定后重新计数，解锁前 checkLock 会拦截
            return "，已连续输错 " + MAX_FAIL + " 次，账号锁定 " + LOCK_MINUTES + " 分钟";
        }
        return "，再输错 " + (MAX_FAIL - a.fails) + " 次将锁定账号 " + LOCK_MINUTES + " 分钟";
    }

    /** 登录成功后调用：清零失败计数 */
    public void reset(String username) {
        attempts.remove(username);
    }
}
