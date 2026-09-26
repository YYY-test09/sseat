package com.wyb.sseat.controller.user;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wyb.sseat.common.CurrentUser;
import com.wyb.sseat.common.Result;
import com.wyb.sseat.dto.ReservationDto;
import com.wyb.sseat.service.ReservationService;
import com.wyb.sseat.vo.ReservationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 用户端预约接口（需登录） */
@RestController
@RequestMapping("/api/user/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    /** 选日期后查各整点时段余量 */
    @GetMapping("/slots")
    public Result<List<Map<String, Object>>> slots(@RequestParam Long roomId,
                                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(reservationService.slotAvailability(roomId, date, CurrentUser.id()));
    }

    /** 选开始/结束时间后查该区间内任一小时被占用的座位号（前端座位网格置灰） */
    @GetMapping("/seats")
    public Result<List<Integer>> seats(@RequestParam Long roomId,
                                       @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                       @RequestParam Integer startHour,
                                       @RequestParam Integer endHour) {
        return Result.ok(reservationService.occupiedSeats(roomId, date, startHour, endHour));
    }

    @PostMapping
    public Result<Void> book(@Valid @RequestBody ReservationDto.BookReq req) {
        reservationService.book(CurrentUser.id(), req);
        return Result.ok();
    }

    @GetMapping("/my")
    public Result<Page<ReservationVO>> my(@RequestParam(defaultValue = "1") long page,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) Integer status) {
        return Result.ok(reservationService.myPage(CurrentUser.id(), page, size, status));
    }

    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        reservationService.cancel(CurrentUser.id(), id);
        return Result.ok();
    }
}
