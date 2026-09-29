package com.wyb.sseat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyb.sseat.common.BizException;
import com.wyb.sseat.entity.Reservation;
import com.wyb.sseat.entity.StudyRoom;
import com.wyb.sseat.mapper.ReservationMapper;
import com.wyb.sseat.mapper.StudyRoomMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 自习室业务：管理端增删改查 + 前台浏览（含剩余名额计算）。
 * 前台列表页接入 Redis Cache-Aside：
 * - key 带版本号 cache:room:page:v{ver}:...，自习室增删改时 INCR 版本号=整体作废
 *   （比 SCAN 删散列 key 简单可靠，读多写少场景粗粒度失效完全够用）；
 * - TTL 60 秒兜底——卡片上的"剩余名额"本就是参考值，真正的余量以预约接口的实时校验为准，
 *   预约导致的短暂陈旧可接受；Redis 故障时自动回源查库（降级不影响可用性）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoomService {

    private final StudyRoomMapper roomMapper;
    private final ReservationMapper reservationMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    private static final String PAGE_VER_KEY = "cache:room:page:ver";
    private static final Duration PAGE_TTL = Duration.ofSeconds(60);

    /**
     * 分页查询。
     * @param adminView true=管理端看全部(含停用)，不缓存；false=前台开放中列表，走缓存
     */
    public Page<StudyRoom> page(long pageNo, long size, String keyword, String type, Integer status, boolean adminView) {
        if (adminView) {
            return queryPage(pageNo, size, keyword, type, status);
        }
        return cachedPage(pageNo, size, keyword, type, status);
    }

    private Page<StudyRoom> queryPage(long pageNo, long size, String keyword, String type, Integer status) {
        LambdaQueryWrapper<StudyRoom> q = new LambdaQueryWrapper<StudyRoom>()
                .like(keyword != null && !keyword.isBlank(), StudyRoom::getName, keyword)
                .eq(type != null && !type.isBlank(), StudyRoom::getType, type)
                .eq(status != null, StudyRoom::getStatus, status)
                .orderByDesc(StudyRoom::getId);
        return roomMapper.selectPage(new Page<>(pageNo, size), q);
    }

    private Page<StudyRoom> cachedPage(long pageNo, long size, String keyword, String type, Integer status) {
        String ver = readVer();
        String key = "cache:room:page:v" + ver + ":" + pageNo + ":" + size + ":"
                + (keyword == null ? "" : keyword) + ":" + (type == null ? "" : type)
                + ":" + (status == null ? "" : status);
        // 1) 读缓存
        try {
            String json = redis.opsForValue().get(key);
            if (json != null) {
                Map<String, Object> hit = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
                Page<StudyRoom> page = new Page<>(pageNo, size, ((Number) hit.get("total")).longValue());
                page.setRecords(objectMapper.convertValue(hit.get("records"), new TypeReference<List<StudyRoom>>() {}));
                return page;
            }
        } catch (Exception e) {
            log.warn("自习室列表缓存读取失败，回源查库: {}", e.getMessage());
        }
        // 2) 未命中查库并回填
        Page<StudyRoom> page = queryPage(pageNo, size, keyword, type, status);
        page.getRecords().forEach(this::fillRemaining); // 前台卡片显示今日剩余名额
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(
                            Map.of("total", page.getTotal(), "records", page.getRecords())),
                    PAGE_TTL);
        } catch (Exception e) {
            log.warn("自习室列表缓存写入失败: {}", e.getMessage());
        }
        return page;
    }

    private String readVer() {
        try {
            String v = redis.opsForValue().get(PAGE_VER_KEY);
            return v == null ? "0" : v;
        } catch (Exception e) {
            return "0"; // Redis 不可用时后续读写缓存同样会走异常分支自动回源，这里的 key 只是形式
        }
    }

    /** 版本号自增=所有分页缓存键整体作废 */
    private void invalidatePageCache() {
        try {
            redis.opsForValue().increment(PAGE_VER_KEY);
        } catch (Exception e) {
            log.warn("自习室列表缓存失效操作跳过: {}", e.getMessage());
        }
    }

    public StudyRoom detail(Long id, boolean adminView) {
        StudyRoom room = roomMapper.selectById(id);
        if (room == null) throw new BizException("自习室不存在");
        if (!adminView) fillRemaining(room);
        return room;
    }

    public void save(StudyRoom room) {
        boolean isNew = room.getId() == null;
        // 必填/默认值逻辑仅对"新增"生效——更新走部分字段更新(MP 忽略 null)，
        // 若在更新时也设默认值会把库里原有 openStart 等字段错误覆盖（早期实现的真实缺陷，测试并发场景时暴露）
        if (isNew) {
            if (room.getName() == null || room.getName().isBlank()) throw new BizException("请输入自习室名称");
            if (room.getSeatCount() == null || room.getSeatCount() <= 0) throw new BizException("座位数必须大于0");
            if (room.getMaxCapacity() == null || room.getMaxCapacity() <= 0) {
                room.setMaxCapacity(room.getSeatCount()); // 不填默认等于座位数
            }
            if (room.getOpenStart() == null) room.setOpenStart(8);
            if (room.getOpenEnd() == null) room.setOpenEnd(21);
            if (room.getDiscount() == null) room.setDiscount(java.math.BigDecimal.ONE);
        }
        // 以下约束在"传了该字段"时才校验（兼容管理端整单提交与部分更新两种用法）
        if (room.getName() != null && room.getName().isBlank()) throw new BizException("请输入自习室名称");
        if (room.getSeatCount() != null && room.getSeatCount() <= 0) throw new BizException("座位数必须大于0");
        if (room.getMaxCapacity() != null && room.getSeatCount() != null
                && room.getMaxCapacity() > room.getSeatCount()) {
            throw new BizException("每时段容量不能超过座位数");
        }
        if (room.getOpenStart() != null && room.getOpenEnd() != null
                && room.getOpenStart() >= room.getOpenEnd()) {
            throw new BizException("开放结束整点必须大于起始整点");
        }

        if (isNew) {
            roomMapper.insert(room);
        } else {
            if (roomMapper.selectById(room.getId()) == null) throw new BizException("自习室不存在");
            roomMapper.updateById(room);
        }
        invalidatePageCache();
    }

    /** 删除前检查：存在未结束的有效预约时不允许删除，避免预约数据悬空 */
    public void delete(Long id) {
        LocalDateTime now = LocalDateTime.now();
        boolean hasFuture = reservationMapper.exists(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getRoomId, id)
                .in(Reservation::getStatus, 0, 1)
                .and(w -> w.gt(Reservation::getReserveDate, LocalDate.now())
                        .or(q -> q.eq(Reservation::getReserveDate, LocalDate.now())
                                .gt(Reservation::getEndHour, now.getHour()))));
        if (hasFuture) throw new BizException("该自习室存在未完成的预约，请先处理后再删除");
        roomMapper.deleteById(id);
        invalidatePageCache();
    }

    /**
     * 计算"今日剩余名额"：取今日所有未开始的整点时段中，剩余容量的最小值。
     * （展示在卡片上的保守值；区间占用 = 该预约的 start~end 覆盖了此小时）
     */
    private void fillRemaining(StudyRoom room) {
        LocalDate today = LocalDate.now();
        int nowHour = LocalDateTime.now().getHour();
        // 今日该室仍有效的预约（status 0/1；取消时已置 seatNo=null 不会进来）
        List<Reservation> todays = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getRoomId, room.getId())
                .eq(Reservation::getReserveDate, today)
                .in(Reservation::getStatus, 0, 1)
                .isNotNull(Reservation::getSeatNo));
        int minRemaining = Integer.MAX_VALUE;
        for (int h = room.getOpenStart(); h < room.getOpenEnd(); h++) {
            if (h < nowHour + 1) continue; // 已开始的时段不计
            final int hour = h;
            long used = todays.stream()
                    .filter(r -> r.getStartHour() <= hour
                            && hour < (r.getEndHour() == null ? r.getStartHour() + 1 : r.getEndHour()))
                    .count();
            minRemaining = Math.min(minRemaining, room.getMaxCapacity() - (int) used);
        }
        room.setRemaining(minRemaining == Integer.MAX_VALUE ? 0 : Math.max(minRemaining, 0));
    }
}
