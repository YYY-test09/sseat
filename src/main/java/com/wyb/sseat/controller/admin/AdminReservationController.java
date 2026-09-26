package com.wyb.sseat.controller.admin;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wyb.sseat.common.Result;
import com.wyb.sseat.dto.ReservationDto;
import com.wyb.sseat.service.ReservationService;
import com.wyb.sseat.vo.ReservationVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** 管理端预约管理接口：查看所有用户预约、筛选、新增/修改/删除处理异常数据 */
@Tag(name="07-管理端-预约")
@RestController
@RequestMapping("/api/admin/reservations")
@RequiredArgsConstructor
public class AdminReservationController {

    private final ReservationService reservationService;

    @GetMapping
    public Result<Page<ReservationVO>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Integer status,
                                            @RequestParam(required = false)
                                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.ok(reservationService.adminPage(page, size, keyword, status, date));
    }

    /** 新增/修改合一：body 带 id 为修改 */
    @PostMapping
    public Result<Void> save(@Valid @RequestBody ReservationDto.AdminSaveReq req) {
        reservationService.adminSave(req);
        return Result.ok();
    }

    /** 快捷改状态：0待确认 1已确认 2已完成 3已取消 */
    @PutMapping("/status")
    public Result<Void> status(@Valid @RequestBody ReservationDto.StatusReq req) {
        reservationService.adminUpdateStatus(req.getId(), req.getStatus());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reservationService.adminDelete(id);
        return Result.ok();
    }
}
