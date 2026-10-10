package com.wyb.sseat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wyb.sseat.common.BizException;
import com.wyb.sseat.common.RedisLock;
import com.wyb.sseat.dto.ReservationDto;
import com.wyb.sseat.entity.Reservation;
import com.wyb.sseat.entity.StudyRoom;
import com.wyb.sseat.entity.User;
import com.wyb.sseat.mapper.ReservationMapper;
import com.wyb.sseat.mapper.StudyRoomMapper;
import com.wyb.sseat.mapper.UserMapper;
import com.wyb.sseat.vo.ReservationVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 预约核心业务（时段为用户自选的整点区间 startHour~endHour，如 9~12 表示 09:00-12:00）：
 * - 时段余量：查该日该室全部有效预约，内存逐小时统计"覆盖了该小时的预约数"（不写复杂 SQL）；
 *   读路径（余量参考/占用座位）走 Redis 缓存（当日占用数据，TTL 30s + 写操作版本号失效），
 *   写路径（book 临界区）强制实时查库
 * - 冲突校验：①所选区间内每一小时都不能超过容量 ②同一座位与已有预约时间段不能重叠
 *   ③同一用户与自己的已有预约时间不能重叠（一人同时段只能有一个座位）
 *   ④并发防线：Redis 分布式锁(室+日期粒度)串行化"校验+插入"临界区，
 *   数据库唯一索引(室,日期,起始整点,座位)最后兜底
 * - 取消：状态置 3 且 seatNo 置 null 释放座位（唯一索引允许多 NULL）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationService {

    /** 最多可提前预约天数 */
    private static final int MAX_ADVANCE_DAYS = 7;
    /** 时段开始后允许签到的分钟数，超时视为违约（座位自动释放） */
    public static final int CHECK_IN_GRACE_MINUTES = 15;
    /** 当日占用数据的缓存版本号 key（任何预约写操作 INCR 它=全部日缓存作废） */
    private static final String RES_VER = "cache:res:ver";
    /** 日占用缓存 TTL：余量本就是"参考值"，短陈旧由预约接口的实时校验兜底正确性 */
    private static final Duration DAY_CACHE_TTL = Duration.ofSeconds(30);

    private final ReservationMapper reservationMapper;
    private final StudyRoomMapper roomMapper;
    private final UserMapper userMapper;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RedisLock redisLock;

    /** 某日各整点参考余量：[{hour, label, remaining, capacity, past, bookedByMe}] */
    public List<Map<String, Object>> slotAvailability(Long roomId, LocalDate date, Long userId) {
        StudyRoom room = mustRoom(roomId);
        if (room.getStatus() != 1) throw new BizException("该自习室已停用，暂不可预约");
        List<Reservation> used = dayReservationsCached(roomId, date);

        int nowHour = LocalDateTime.now().getHour();
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int h = room.getOpenStart(); h < room.getOpenEnd(); h++) {
            final int hour = h;
            int capacity = room.getMaxCapacity();
            int booked = (int) used.stream().filter(r -> coversHour(r, hour)).count();
            boolean mine = userId != null && used.stream()
                    .anyMatch(r -> r.getUserId().equals(userId) && coversHour(r, hour));
            boolean past = date.isEqual(LocalDate.now()) && h <= nowHour; // 已开始的整点不可约
            slots.add(Map.of(
                    "hour", h,
                    "label", String.format("%02d:00-%02d:00", h, h + 1),
                    "capacity", capacity,
                    "remaining", past ? 0 : Math.max(capacity - booked, 0),
                    "past", past,
                    "bookedByMe", mine));
        }
        return slots;
    }

    /** start~end 区间内任一小时被占用的座位号（前端座位网格置灰，自己的也算占用避免重复约） */
    public List<Integer> occupiedSeats(Long roomId, LocalDate date, int startHour, int endHour) {
        mustRoom(roomId);
        // 与余量共用同一份"当日占用"缓存：内存过滤重叠区间，省一次 SQL
        return dayReservationsCached(roomId, date).stream()
                .filter(r -> r.getSeatNo() != null && overlaps(r, startHour, endHour))
                .map(Reservation::getSeatNo).distinct().sorted().toList();
    }

    /** 用户提交预约 */
    public void book(Long userId, ReservationDto.BookReq req) {
        StudyRoom room = mustRoom(req.getRoomId());
        LocalDate today = LocalDate.now();
        int start = req.getStartHour();
        int end = req.getEndHour();
        if (req.getReserveDate().isBefore(today) || req.getReserveDate().isAfter(today.plusDays(MAX_ADVANCE_DAYS))) {
            throw new BizException("只能预约今天起 " + MAX_ADVANCE_DAYS + " 天内的日期");
        }
        if (end <= start) throw new BizException("结束时间必须晚于开始时间");
        if (start < room.getOpenStart() || end > room.getOpenEnd()) {
            throw new BizException(String.format("所选时段须在开放时间 %02d:00-%02d:00 内",
                    room.getOpenStart(), room.getOpenEnd()));
        }
        if (req.getReserveDate().isEqual(today) && start <= LocalDateTime.now().getHour()) {
            throw new BizException("开始时段已过，请重新选择");
        }
        if (req.getSeatNo() > room.getSeatCount()) {
            throw new BizException("座位号超出该自习室座位总数（共" + room.getSeatCount() + "座）");
        }
        // 并发防线：自习室+日期粒度的 Redis 分布式锁，把"读占用→校验→写库"整段串行化，
        // 关闭容量/座位校验与插入之间的竞态窗口（10 秒 TTL 防持锁进程宕机死锁）
        String lockKey = "lock:book:" + req.getRoomId() + ":" + req.getReserveDate();
        String lockToken = null;
        boolean lockSkipped = false;
        try {
            lockToken = redisLock.tryLock(lockKey, Duration.ofSeconds(10));
        } catch (Exception e) {
            lockSkipped = true; // Redis 故障降级：没有锁仍有"一人一位+座位冲突校验+唯一索引"三道防线
            log.warn("Redis 不可用，预约分布式锁降级: {}", e.getMessage());
        }
        if (lockToken == null && !lockSkipped) {
            throw new BizException("同时预约该自习室的人较多，请稍后再试");
        }
        try {
            // 同一人限制：本人在该日期已有时间重叠的预约（不限自习室）则拒绝——一个人同一时间只能坐一个位子
            List<Reservation> mine = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                    .eq(Reservation::getUserId, userId)
                    .eq(Reservation::getReserveDate, req.getReserveDate())
                    .in(Reservation::getStatus, 0, 1));
            if (mine.stream().anyMatch(r -> overlaps(r, start, end))) {
                throw new BizException("您在该时段已有预约，同一时间只能保留一个座位（可先取消原预约）");
            }
            // 临界区内必须查实时库——不能走可能陈旧 30s 的缓存
            List<Reservation> used = dayReservations(req.getRoomId(), req.getReserveDate());
            // 容量校验：所选区间内每个整点的占用数都不能达到容量
            for (int h = start; h < end; h++) {
                final int hour = h;
                long booked = used.stream().filter(r -> coversHour(r, hour)).count();
                if (booked >= room.getMaxCapacity()) {
                    throw new BizException(String.format("%02d:00-%02d:00 时段已约满，请调整起止时间", hour, hour + 1));
                }
            }
            // 座位冲突校验：该座位已有预约与所选区间重叠则拒绝
            boolean seatConflict = used.stream().anyMatch(r -> r.getSeatNo() != null
                    && r.getSeatNo().equals(req.getSeatNo()) && overlaps(r, start, end));
            if (seatConflict) throw new BizException("该座位在所选时段内已被预约，请换一个座位或调整时段");

            Reservation r = new Reservation();
            r.setUserId(userId);
            r.setRoomId(req.getRoomId());
            r.setReserveDate(req.getReserveDate());
            r.setStartHour(start);
            r.setEndHour(end);
            r.setSeatNo(req.getSeatNo());
            r.setStatus(0); // 待确认
            try {
                reservationMapper.insert(r);
            } catch (DuplicateKeyException e) {
                // 锁降级期间或跨实例极端情况，同座位同时段唯一索引是最后防线
                throw new BizException("该座位刚刚已被预约，请换一个座位");
            }
            bumpResVersion(); // 占用变了：当日余量/占用座位缓存整体作废
        } finally {
            if (lockToken != null) {
                redisLock.unlock(lockKey, lockToken);
            }
        }
    }

    /** 用户取消预约：仅限本人、未开始、未取消 */
    public void cancel(Long userId, Long id) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null || !r.getUserId().equals(userId)) throw new BizException("预约记录不存在");
        if (r.getStatus() == 2) throw new BizException("已完成的预约不能取消");
        if (r.getStatus() == 3) throw new BizException("该预约已取消过了");
        if (isStarted(r)) throw new BizException("时段已开始，无法取消");
        // 注意：MP 的 updateById 默认忽略 null 字段，置空 seatNo 必须用 UpdateWrapper 显式 set
        reservationMapper.update(null, new LambdaUpdateWrapper<Reservation>()
                .eq(Reservation::getId, id)
                .set(Reservation::getStatus, 3)
                .set(Reservation::getSeatNo, null));
        bumpResVersion();
    }

    /** 用户签到：时段开始后 15 分钟内有效；签到后即使过期也只置"已完成"不置违约 */
    public void checkIn(Long userId, Long id) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null || !r.getUserId().equals(userId)) throw new BizException("预约记录不存在");
        if (r.getStatus() != 0 && r.getStatus() != 1) throw new BizException("该预约当前状态无法签到");
        if (r.getCheckInTime() != null) throw new BizException("已签到，无需重复操作");
        LocalDateTime start = r.getReserveDate().atTime(r.getStartHour(), 0);
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(start)) throw new BizException("还没到预约开始时间，无法签到");
        if (now.isAfter(start.plusMinutes(CHECK_IN_GRACE_MINUTES))) {
            throw new BizException("已超过开始后 " + CHECK_IN_GRACE_MINUTES + " 分钟，签到通道已关闭");
        }
        Reservation update = new Reservation();
        update.setId(id);
        update.setCheckInTime(now);
        reservationMapper.updateById(update);
    }

    /** 我的预约（分页，按日期倒序） */
    public Page<ReservationVO> myPage(Long userId, long pageNo, long size, Integer status) {
        Page<Reservation> raw = reservationMapper.selectPage(new Page<>(pageNo, size),
                new LambdaQueryWrapper<Reservation>()
                        .eq(Reservation::getUserId, userId)
                        .eq(status != null, Reservation::getStatus, status)
                        .orderByDesc(Reservation::getReserveDate).orderByDesc(Reservation::getStartHour));
        return toVOPage(raw);
    }

    /** 管理端预约列表：可按自习室名/用户名关键词、状态、日期筛选 */
    public Page<ReservationVO> adminPage(long pageNo, long size, String keyword, Integer status, LocalDate date) {
        LambdaQueryWrapper<Reservation> q = new LambdaQueryWrapper<Reservation>()
                .eq(status != null, Reservation::getStatus, status)
                .eq(date != null, Reservation::getReserveDate, date)
                .orderByDesc(Reservation::getId);
        if (keyword != null && !keyword.isBlank()) {
            // 先按关键词找用户id与自习室id，再 in 过滤（简单可靠，不写联表 SQL）
            List<Long> userIds = userMapper.selectList(new LambdaQueryWrapper<User>()
                    .like(User::getUsername, keyword).or().like(User::getNickname, keyword))
                    .stream().map(User::getId).toList();
            List<Long> roomIds = roomMapper.selectList(new LambdaQueryWrapper<StudyRoom>()
                    .like(StudyRoom::getName, keyword)).stream().map(StudyRoom::getId).toList();
            if (userIds.isEmpty() && roomIds.isEmpty()) {
                return new Page<>(pageNo, size, 0);
            }
            q.and(w -> {
                if (!userIds.isEmpty()) w.in(Reservation::getUserId, userIds).or();
                if (!roomIds.isEmpty()) w.in(Reservation::getRoomId, roomIds);
            });
        }
        return toVOPage(reservationMapper.selectPage(new Page<>(pageNo, size), q));
    }

    /** 管理端新增/修改预约（处理特殊需求或异常数据修正） */
    public void adminSave(ReservationDto.AdminSaveReq req) {
        StudyRoom room = mustRoom(req.getRoomId());
        if (req.getEndHour() <= req.getStartHour()) throw new BizException("结束时间必须晚于开始时间");
        if (req.getSeatNo() > room.getSeatCount()) throw new BizException("座位号超出座位总数");
        // 同一座位时间段重叠检查（编辑时排除自己这条记录）
        boolean conflict = reservationMapper.exists(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getRoomId, req.getRoomId())
                .eq(Reservation::getReserveDate, req.getReserveDate())
                .eq(Reservation::getSeatNo, req.getSeatNo())
                .in(Reservation::getStatus, 0, 1)
                .ne(req.getId() != null, Reservation::getId, req.getId())
                .lt(Reservation::getStartHour, req.getEndHour())
                .gt(Reservation::getEndHour, req.getStartHour()));
        if (conflict) throw new BizException("该座位在所选时段内已有预约，请调整时段或座位");
        Reservation r = new Reservation();
        r.setId(req.getId());
        r.setUserId(req.getUserId());
        r.setRoomId(req.getRoomId());
        r.setReserveDate(req.getReserveDate());
        r.setStartHour(req.getStartHour());
        r.setEndHour(req.getEndHour());
        r.setSeatNo(req.getSeatNo());
        r.setStatus(req.getStatus());
        try {
            if (req.getId() == null) {
                reservationMapper.insert(r);
            } else {
                Reservation exist = reservationMapper.selectById(req.getId());
                if (exist == null) throw new BizException("预约记录不存在");
                reservationMapper.updateById(r);
            }
        } catch (DuplicateKeyException e) {
            throw new BizException("该时段该座位已被其他预约占用");
        }
        bumpResVersion();
    }

    /** 管理端修改状态（确认/完成/取消） */
    public void adminUpdateStatus(Long id, Integer status) {
        Reservation exist = reservationMapper.selectById(id);
        if (exist == null) throw new BizException("预约记录不存在");
        if (status == 3) {
            // 管理员取消同样释放座位：null 字段必须用 UpdateWrapper 显式 set（见 cancel 注释）
            reservationMapper.update(null, new LambdaUpdateWrapper<Reservation>()
                    .eq(Reservation::getId, id)
                    .set(Reservation::getStatus, 3)
                    .set(Reservation::getSeatNo, null));
        } else {
            Reservation update = new Reservation();
            update.setId(id);
            update.setStatus(status);
            reservationMapper.updateById(update);
        }
        bumpResVersion();
    }

    public void adminDelete(Long id) {
        reservationMapper.deleteById(id);
        bumpResVersion();
    }

    /**
     * 定时任务调用，巡检所有待确认/已确认预约，做两条状态流转：
     * ① 违约释放：时段开始超过 15 分钟仍未签到 → 置"已取消"并释放座位；
     * ② 正常完结：已签到且时段整体结束 → 置"已完成"。
     */
    public int completeExpired() {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> actives = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .in(Reservation::getStatus, 0, 1));
        int count = 0;
        for (Reservation r : actives) {
            LocalDateTime start = r.getReserveDate().atTime(r.getStartHour(), 0);
            if (r.getCheckInTime() == null) {
                // 未签到且已过签到窗口 → 违约取消、释放座位
                // isNull 前置条件：与用户"踩点签到"竞争时由数据库最终裁决，巡检只动确实没签到的行
                if (now.isAfter(start.plusMinutes(CHECK_IN_GRACE_MINUTES))) {
                    int affected = reservationMapper.update(null, new LambdaUpdateWrapper<Reservation>()
                            .eq(Reservation::getId, r.getId())
                            .isNull(Reservation::getCheckInTime)
                            .set(Reservation::getStatus, 3)
                            .set(Reservation::getSeatNo, null));
                    count += affected; // 以真实更新行数计数：恰好踩线签到的行更新 0 次，不误报
                }
            } else if (hasEndedAt(r, now)) {
                // 已签到且时段结束 → 已完成
                Reservation update = new Reservation();
                update.setId(r.getId());
                update.setStatus(2);
                reservationMapper.updateById(update);
                count++;
            }
        }
        if (count > 0) bumpResVersion(); // 违约释放改变了占用，缓存整体作废
        return count;
    }

    // ---------- 缓存辅助 ----------

    /** 当日占用数据（status 0/1）读缓存版本：slotAvailability / occupiedSeats 共用 */
    private List<Reservation> dayReservationsCached(Long roomId, LocalDate date) {
        String key = null;
        try {
            String ver = redis.opsForValue().get(RES_VER);
            key = "cache:res:v" + (ver == null ? "0" : ver) + ":day:" + roomId + ":" + date;
            String json = redis.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<List<Reservation>>() {});
            }
        } catch (Exception e) {
            log.warn("预约日缓存读取失败，回源查库: {}", e.getMessage());
            key = null;
        }
        List<Reservation> list = dayReservations(roomId, date);
        if (key != null) {
            try {
                redis.opsForValue().set(key, objectMapper.writeValueAsString(list), DAY_CACHE_TTL);
            } catch (Exception e) {
                log.warn("预约日缓存写入失败: {}", e.getMessage());
            }
        }
        return list;
    }

    /** 预约数据任何变更后 INCR 版本号：所有 "cache:res:v{ver}:day:*" 键整体作废（旧键等 TTL 自然清理） */
    private void bumpResVersion() {
        try {
            redis.opsForValue().increment(RES_VER);
        } catch (Exception e) {
            log.warn("预约缓存版本自增失败(最长30s后自动过期回源): {}", e.getMessage());
        }
    }

    // ---------- 私有 ----------

    /** 预约是否已"开始"（按起始整点判断，用于取消限制） */
    private boolean isStarted(Reservation r) {
        LocalDate d = r.getReserveDate();
        LocalDateTime now = LocalDateTime.now();
        return d.isBefore(now.toLocalDate())
                || (d.isEqual(now.toLocalDate()) && r.getStartHour() <= now.getHour());
    }

    /** 预约是否已"整体结束"（按结束整点判断，用于定时任务置已完成） */
    private boolean hasEndedAt(Reservation r, LocalDateTime now) {
        LocalDate d = r.getReserveDate();
        int end = r.getEndHour() == null ? r.getStartHour() + 1 : r.getEndHour();
        return d.isBefore(now.toLocalDate())
                || (d.isEqual(now.toLocalDate()) && end <= now.getHour());
    }

    /** 该预约占用的时间段是否覆盖某个整点 h（即 start <= h < end） */
    private boolean coversHour(Reservation r, int hour) {
        if (r.getSeatNo() == null) return false; // 已取消释放座位的不算占用
        int end = r.getEndHour() == null ? r.getStartHour() + 1 : r.getEndHour();
        return r.getStartHour() <= hour && hour < end;
    }

    /** 该预约与新区间 [start, end) 是否时间重叠 */
    private boolean overlaps(Reservation r, int start, int end) {
        int rEnd = r.getEndHour() == null ? r.getStartHour() + 1 : r.getEndHour();
        return r.getStartHour() < end && rEnd > start;
    }

    private List<Reservation> dayReservations(Long roomId, LocalDate date) {
        return reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getRoomId, roomId)
                .eq(Reservation::getReserveDate, date)
                .in(Reservation::getStatus, 0, 1));
    }

    private StudyRoom mustRoom(Long roomId) {
        StudyRoom room = roomMapper.selectById(roomId);
        if (room == null) throw new BizException("自习室不存在");
        return room;
    }

    /** 批量补齐自习室名与用户信息，避免 N+1 查询 */
    private Page<ReservationVO> toVOPage(Page<Reservation> raw) {
        Page<ReservationVO> out = new Page<>(raw.getCurrent(), raw.getSize(), raw.getTotal());
        List<Reservation> records = raw.getRecords();
        if (records.isEmpty()) { out.setRecords(List.of()); return out; }
        Map<Long, StudyRoom> roomMap = roomMapper.selectBatchIds(
                        records.stream().map(Reservation::getRoomId).distinct().toList())
                .stream().collect(Collectors.toMap(StudyRoom::getId, Function.identity()));
        Map<Long, User> userMap = userMapper.selectBatchIds(
                        records.stream().map(Reservation::getUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        out.setRecords(records.stream().map(r -> {
            ReservationVO v = new ReservationVO();
            v.setId(r.getId());
            v.setUserId(r.getUserId());
            v.setRoomId(r.getRoomId());
            v.setReserveDate(r.getReserveDate());
            v.setStartHour(r.getStartHour());
            v.setEndHour(r.getEndHour());
            v.setSeatNo(r.getSeatNo());
            v.setStatus(r.getStatus());
            v.setCheckInTime(r.getCheckInTime());
            v.setCreateTime(r.getCreateTime());
            StudyRoom room = roomMap.get(r.getRoomId());
            if (room != null) { v.setRoomName(room.getName()); v.setRoomType(room.getType()); }
            User u = userMap.get(r.getUserId());
            if (u != null) { v.setUsername(u.getUsername()); v.setNickname(u.getNickname()); }
            return v;
        }).toList());
        return out;
    }
}
