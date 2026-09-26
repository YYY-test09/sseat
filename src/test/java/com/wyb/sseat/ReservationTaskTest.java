package com.wyb.sseat;

import com.wyb.sseat.entity.Reservation;
import com.wyb.sseat.mapper.ReservationMapper;
import com.wyb.sseat.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证定时巡检核心方法（真实DB、事务回滚不留脏数据）：
 * ①过期未签到 → 违约取消(3)且座位号已置NULL释放；
 * ②已签到且时段结束 → 已完成(2)；
 * ③未来时段的预约 → 状态不受影响。
 */
@SpringBootTest
@Transactional
class ReservationTaskTest {
    @Autowired ReservationService reservationService;
    @Autowired ReservationMapper reservationMapper;

    private Reservation insert(LocalDate date, int start, int end, Integer seat, int status, LocalDateTime checkIn) {
        Reservation r = new Reservation();
        r.setUserId(1L); r.setRoomId(1L);
        r.setReserveDate(date);
        r.setStartHour(start); r.setEndHour(end);
        r.setSeatNo(seat); r.setStatus(status);
        r.setCheckInTime(checkIn);
        reservationMapper.insert(r);
        return r;
    }

    @Test
    void noShowExpiredCancelledAndSeatReleased() {
        Reservation r = insert(LocalDate.now().minusDays(1), 10, 12, 500, 0, null);

        reservationService.completeExpired();

        Reservation after = reservationMapper.selectById(r.getId());
        assertEquals(3, after.getStatus(), "过期未签到应违约取消(3)");
        assertNull(after.getSeatNo(), "违约后座位号应置NULL释放");
    }

    @Test
    void checkedInExpiredMarkedDone() {
        Reservation r = insert(LocalDate.now().minusDays(1), 10, 12, 502, 1,
                LocalDate.now().minusDays(1).atTime(10, 2));

        reservationService.completeExpired();

        assertEquals(2, reservationMapper.selectById(r.getId()).getStatus(), "已签到且结束应置已完成(2)");
        assertEquals(502, reservationMapper.selectById(r.getId()).getSeatNo(), "完成的记录保留座位供追溯");
    }

    @Test
    void futureReservationUntouched() {
        Reservation r = insert(LocalDate.now().plusDays(1), 9, 11, 501, 0, null);

        reservationService.completeExpired();

        assertEquals(0, reservationMapper.selectById(r.getId()).getStatus(), "未来预约不应被改动");
    }
}
