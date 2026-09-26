package com.wyb.sseat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wyb.sseat.common.BizException;
import com.wyb.sseat.entity.Reservation;
import com.wyb.sseat.entity.StudyRoom;
import com.wyb.sseat.mapper.ReservationMapper;
import com.wyb.sseat.mapper.StudyRoomMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 自习室业务：管理端增删改查 + 前台浏览（含剩余名额计算） */
@Service
@RequiredArgsConstructor
public class RoomService {

    private final StudyRoomMapper roomMapper;
    private final ReservationMapper reservationMapper;

    /**
     * 分页查询。
     * @param adminView true=管理端看全部(含停用)；false=前台只看开放中的
     */
    public Page<StudyRoom> page(long pageNo, long size, String keyword, String type, Integer status, boolean adminView) {
        LambdaQueryWrapper<StudyRoom> q = new LambdaQueryWrapper<StudyRoom>()
                .like(keyword != null && !keyword.isBlank(), StudyRoom::getName, keyword)
                .eq(type != null && !type.isBlank(), StudyRoom::getType, type)
                .eq(status != null, StudyRoom::getStatus, status)
                .orderByDesc(StudyRoom::getId);
        Page<StudyRoom> page = roomMapper.selectPage(new Page<>(pageNo, size), q);
        if (!adminView) {
            page.getRecords().forEach(this::fillRemaining); // 前台卡片显示今日剩余名额
        }
        return page;
    }

    public StudyRoom detail(Long id, boolean adminView) {
        StudyRoom room = roomMapper.selectById(id);
        if (room == null) throw new BizException("自习室不存在");
        if (!adminView) fillRemaining(room);
        return room;
    }

    public void save(StudyRoom room) {
        if (room.getName() == null || room.getName().isBlank()) throw new BizException("请输入自习室名称");
        if (room.getSeatCount() == null || room.getSeatCount() <= 0) throw new BizException("座位数必须大于0");
        if (room.getMaxCapacity() == null || room.getMaxCapacity() <= 0) {
            room.setMaxCapacity(room.getSeatCount()); // 不填默认等于座位数
        }
        if (room.getMaxCapacity() > room.getSeatCount()) {
            throw new BizException("每时段容量不能超过座位数");
        }
        if (room.getOpenStart() == null) room.setOpenStart(8);
        if (room.getOpenEnd() == null) room.setOpenEnd(21);
        if (room.getOpenStart() >= room.getOpenEnd()) throw new BizException("开放结束整点必须大于起始整点");
        if (room.getDiscount() == null) room.setDiscount(java.math.BigDecimal.ONE);

        if (room.getId() == null) {
            roomMapper.insert(room);
        } else {
            if (roomMapper.selectById(room.getId()) == null) throw new BizException("自习室不存在");
            roomMapper.updateById(room);
        }
    }

    /** 删除前检查：存在未开始的有效预约时不允许删除，避免预约数据悬空 */
    public void delete(Long id) {
        LocalDateTime now = LocalDateTime.now();
        boolean hasFuture = reservationMapper.exists(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getRoomId, id)
                .in(Reservation::getStatus, 0, 1)
                .and(w -> w.gt(Reservation::getReserveDate, LocalDate.now())
                        .or(q -> q.eq(Reservation::getReserveDate, LocalDate.now())
                                .gt(Reservation::getStartHour, now.getHour()))));
        if (hasFuture) throw new BizException("该自习室存在未完成的预约，请先处理后再删除");
        roomMapper.deleteById(id);
    }

    /**
     * 计算"今日剩余名额"：取今日所有未开始的整点时段中，剩余容量的最小值。
     * （展示在卡片上的保守值；真实逐时段余量在预约步骤按日期查询）
     */
    private void fillRemaining(StudyRoom room) {
        LocalDate today = LocalDate.now();
        int nowHour = LocalDateTime.now().getHour();
        // 今日该室各时段已约人数（排除已取消 seatNo=null 的记录）
        List<Reservation> todays = reservationMapper.selectList(new LambdaQueryWrapper<Reservation>()
                .eq(Reservation::getRoomId, room.getId())
                .eq(Reservation::getReserveDate, today)
                .in(Reservation::getStatus, 0, 1)
                .isNotNull(Reservation::getSeatNo));
        Map<Integer, Long> countByHour = todays.stream()
                .collect(Collectors.groupingBy(Reservation::getStartHour, Collectors.counting()));
        int minRemaining = Integer.MAX_VALUE;
        for (int h = room.getOpenStart(); h < room.getOpenEnd(); h++) {
            if (h < nowHour + 1) continue; // 已开始/结束的时段不计
            int used = countByHour.getOrDefault(h, 0L).intValue();
            minRemaining = Math.min(minRemaining, room.getMaxCapacity() - used);
        }
        room.setRemaining(minRemaining == Integer.MAX_VALUE ? 0 : Math.max(minRemaining, 0));
    }
}
