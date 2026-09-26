package com.wyb.sseat.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wyb.sseat.common.Result;
import com.wyb.sseat.entity.StudyRoom;
import com.wyb.sseat.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 管理端-自习室座位管理接口（/api/admin/** 由 Security 保证仅管理员可访问） */
@RestController
@RequestMapping("/api/admin/rooms")
@RequiredArgsConstructor
public class AdminRoomController {

    private final RoomService roomService;

    @GetMapping
    public Result<Page<StudyRoom>> page(@RequestParam(defaultValue = "1") long page,
                                        @RequestParam(defaultValue = "10") long size,
                                        @RequestParam(required = false) String keyword,
                                        @RequestParam(required = false) String type,
                                        @RequestParam(required = false) Integer status) {
        return Result.ok(roomService.page(page, size, keyword, type, status, true));
    }

    @GetMapping("/{id}")
    public Result<StudyRoom> detail(@PathVariable Long id) {
        return Result.ok(roomService.detail(id, true));
    }

    /** 新增/编辑合一：body 不带 id 为新增 */
    @PostMapping
    public Result<Void> save(@RequestBody StudyRoom room) {
        roomService.save(room);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roomService.delete(id);
        return Result.ok();
    }
}
