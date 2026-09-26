package com.wyb.sseat;

import com.wyb.sseat.entity.Reservation;
import com.wyb.sseat.mapper.ReservationMapper;
import com.wyb.sseat.service.ReservationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/** 验证定时任务核心方法:把过期"待确认/已确认"预约置为已完成(真实DB) */
@SpringBootTest
@Transactional
class ReservationTaskTest {
    @Autowired ReservationService reservationService;
    @Autowired ReservationMapper reservationMapper;

    @Test
    void completeExpiredMarksPastActiveAsDone() {
        Reservation r = new Reservation();
        r.setUserId(1L); r.setRoomId(1L);
        r.setReserveDate(LocalDate.now().minusDays(1)); // 昨天,一定已过期
        r.setStartHour(10); r.setSeatNo(500); r.setStatus(0);
        reservationMapper.insert(r);

        int n = reservationService.completeExpired();

        assertTrue(n >= 1, "至少处理到本次插入的过期记录");
        assertEquals(2, reservationMapper.selectById(r.getId()).getStatus(), "过期记录应置为已完成(2)");
        // 未来时段不受影响
        Reservation future = new Reservation();
        future.setUserId(1L); future.setRoomId(1L);
        future.setReserveDate(LocalDate.now().plusDays(1));
        future.setStartHour(9); future.setSeatNo(501); future.setStatus(0);
        reservationMapper.insert(future);
        reservationService.completeExpired();
        assertEquals(0, reservationMapper.selectById(future.getId()).getStatus(), "未来预约不应被改动");
    }
}
