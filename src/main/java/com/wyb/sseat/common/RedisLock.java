package com.wyb.sseat.common;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Redis 分布式锁（SETNX 语义的简化实现）。
 * - 加锁：SET key token NX PX ttl —— 原子占坑，抢不到即已有持有者；
 * - 解锁：Lua 脚本"比对持有者再删除"，防止 A 的锁超时后误删 B 刚拿到的锁；
 * - token 随机串即持有者凭证。
 * 用途：预约提交的"校验+写库"临界区，关闭应用层校验与插入之间的并发窗口。
 * 局限说明：未做看门狗续期与可重入，对本项目 10 秒级短事务足够。
 */
@Component
@RequiredArgsConstructor
public class RedisLock {

    private final StringRedisTemplate redis;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    /** 尝试加锁：成功返回持有者 token，失败返回 null（不阻塞不自旋，由调用方决定提示语） */
    public String tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean ok = redis.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    /** 解锁：仅当持有者是自己才删除（Lua 保证"比对+删除"原子性） */
    public void unlock(String key, String token) {
        if (token != null) {
            redis.execute(UNLOCK_SCRIPT, List.of(key), token);
        }
    }
}
