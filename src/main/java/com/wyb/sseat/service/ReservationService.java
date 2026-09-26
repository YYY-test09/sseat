package com.wyb.sseat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wyb.sseat.common.BizException;
import com.wyb.sseat.dto.ReservationDto;
import com.wyb.sseat.entity.Reservation;
import com.wyb.sseat.entity.StudyRoom;
import com.wyb.sseat.entity.User;
import com.wyb.sseat.mapper.ReservationMapper;
import com.wyb.sseat.mapper.StudyRoomMapper;
import com.wyb.sseat.mapper.UserMapper;
import com.wyb.sseat.vo.ReservationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 预约核心业务：
 * - 时段余量：查该日该室全部有效预约，内存按整点分组计算（不写复杂 SQL）
 * - 冲突校验：座位占用 count 预检 + 数据库唯一索引兜底（防并发超卖）
 * - 取消：状态置 3 且 seatNo 置 null 释放座位（唯一索引允许多 NULL）
 */
@Service
@RequiredArgsConstructor
public class ReservationService {

    /** 最多可提前预约天数 */
    private static final int MAX_ADVANCE_DAYS = 7;

    private final ReservationMapper reservationMapper;
    private final StudyRoomMapper roomMapper;
    private final UserMapper userMapper;

    /** 某日各整点时段余量：[{hour, label, remaining, capacity, bookedByMe}] */
    public List<Map<String, Object>> slotAvailability(Long roomId, LocalDate date, Long userId) {
        StudyRoom room = mustRoom(roomId);
        if (room.getStatus() != 1) throw new BizException("该自习室已停用，暂不可预约");
        List<Reservation> used = dayReservations(roomId, date);
        Map<Integer, Long> countByHour = used.stream()
                .filter(r -> r.getSeatNo() != null && r.getStatus() != 3)
                .collect(Collectors.groupingBy(Reservation::getStartHour, Collectors.counting()));
        Map<Integer, Boolean> mineByHour = used.stream()
                .filter(r -> r.getUserId().equals(userId) && r.getStatus() != 3)
                .collect(Collectors.toMap(Reservation::getStartHour, r -> true, (a, b) -> a));

        int nowHour = LocalDateTime.now().getHour();
        List<Map<String, Object>> slots = new ArrayList<>();
        for (int h = room.getOpenStart(); h < room.getOpenEnd(); h++) {
            int capacity = room.getMaxCapacity();
            int booked = countByHour.getOrDefault(h, 0L).intValue();
            boolean past = date.isEqual(LocalDate.now()) && h <= nowHour; // 已开始的时段不可约
            slots.add(Map.of(
                    "hour", h,
                    "label", String.format("%02d:00-%02d:00", h, h + 1),
                    "capacity", capacity,
                    "remaining", past ? 0 : Math.max(capacity - booked, 0),
                    "past", past,
                    "bookedByMe", mineByHour.getOrDefault(h, false)));
        }
        return slots;
    }

    /** 某日某时段已被占用的座位号列表（前端渲染座位网格时置灰，自己的也算占用避免重复约） */
    public List<Integer> occupiedSeats(Long roomId, LocalDate date, int hour) {
        mustRoom(roomId);
        List<Reservation> list = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getRoomId, roomId)
                .eq(Reservation::getReserveDate, date)
                .eq(Reservation::getStartHour, hour)
                .in(Reservation::getStatus, 0, 1)
                .isNotNull(Reservation::getSeatNo));
        return list.stream().map(Reservation::getSeatNo).sorted().toList();
    }

    /** 用户提交预约 */
    public void book(Long userId, ReservationDto.BookReq req) {
        StudyRoom room = mustRoom(req.getRoomId());
        LocalDate today = LocalDate.now();
        if (req.getReserveDate().isBefore(today) || req.getReserveDate().isAfter(today.plusDays(MAX_ADVANCE_DAYS))) {
            throw new BizException("只能预约今天起 " + MAX_ADVANCE_DAYS + " 天内的日期");
        }
        if (req.getStartHour() < room.getOpenStart() || req.getStartHour() >= room.getOpenEnd()) {
            throw new BizException("该时段不在开放时间内");
        }
        if (req.getReserveDate().isEqual(today) && req.getStartHour() <= LocalDateTime.now().getHour()) {
            throw new BizException("该时段已开始或已结束");
        }
        if (req.getSeatNo() > room.getSeatCount()) {
            throw new BizException("座位号超出该自习室座位总数（共" + room.getSeatCount() + "座）");
        }
        long booked = reservationMapper.selectCount(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getRoomId, req.getRoomId())
                .eq(Reservation::getReserveDate, req.getReserveDate())
                .eq(Reservation::getStartHour, req.getStartHour())
                .in(Reservation::getStatus, 0, 1)
                .isNotNull(Reservation::getSeatNo));
        if (booked >= room.getMaxCapacity()) {
            throw new BizException("该时段已约满，请选择其他时段");
        }

        Reservation r = new Reservation();
        r.setUserId(userId);
        r.setRoomId(req.getRoomId());
        r.setReserveDate(req.getReserveDate());
        r.setStartHour(req.getStartHour());
        r.setSeatNo(req.getSeatNo());
        r.setStatus(0); // 待确认
        try {
            reservationMapper.insert(r);
        } catch (DuplicateKeyException e) {
            // 并发下 count 预检可能都通过，唯一索引是最后防线
            throw new BizException("该座位刚刚已被预约，请换一个座位");
        }
    }

    /** 用户取消预约：仅限本人、未开始、未取消 */
    public void cancel(Long userId, Long id) {
        Reservation r = reservationMapper.selectById(id);
        if (r == null || !r.getUserId().equals(userId)) throw new BizException("预约记录不存在");
        if (r.getStatus() == 2) throw new BizException("已完成的预约不能取消");
        if (r.getStatus() == 3) throw new BizException("该预约已取消过了");
        if (isStarted(r)) throw new BizException("时段已开始，无法取消");
        Reservation update = new Reservation();
        update.setId(id);
        update.setStatus(3);
        update.setSeatNo(null); // 关键：置 NULL 释放座位
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

    /** 管理端新增/修改预约 */
    public void adminSave(ReservationDto.AdminSaveReq req) {
        StudyRoom room = mustRoom(req.getRoomId());
        if (req.getSeatNo() > room.getSeatCount()) throw new BizException("座位号超出座位总数");
        Reservation r = new Reservation();
        r.setId(req.getId());
        r.setUserId(req.getUserId());
        r.setRoomId(req.getRoomId());
        r.setReserveDate(req.getReserveDate());
        r.setStartHour(req.getStartHour());
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
    }

    /** 管理端修改状态（确认/完成/取消） */
    public void adminUpdateStatus(Long id, Integer status) {
        Reservation exist = reservationMapper.selectById(id);
        if (exist == null) throw new BizException("预约记录不存在");
        Reservation update = new Reservation();
        update.setId(id);
        update.setStatus(status);
        if (status == 3) update.setSeatNo(null); // 管理员取消同样释放座位
        reservationMapper.updateById(update);
    }

    public void adminDelete(Long id) {
        reservationMapper.deleteById(id);
    }

    /** 定时任务调用：把已结束的待确认/已确认预约置为已完成 */
    public int completeExpired() {
        LocalDateTime now = LocalDateTime.now();
        List<Reservation> actives = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .in(Reservation::getStatus, 0, 1));
        int count = 0;
        for (Reservation r : actives) {
            if (isStartedAt(r, now)) {
                Reservation update = new Reservation();
                update.setId(r.getId());
                update.setStatus(2);
                reservationMapper.updateById(update);
                count++;
            }
        }
        return count;
    }

    // ---------- 私有 ----------

    private boolean isStarted(Reservation r) {
        return isStartedAt(r, LocalDateTime.now());
    }

    private boolean isStartedAt(Reservation r, LocalDateTime now) {
        LocalDate d = r.getReserveDate();
        return d.isBefore(now.toLocalDate())
                || (d.isEqual(now.toLocalDate()) && r.getStartHour() <= now.getHour());
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
            v.setSeatNo(r.getSeatNo());
            v.setStatus(r.getStatus());
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
