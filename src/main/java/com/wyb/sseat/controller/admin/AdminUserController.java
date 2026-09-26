package com.wyb.sseat.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wyb.sseat.common.CurrentUser;
import com.wyb.sseat.common.Result;
import com.wyb.sseat.dto.UserDto;
import com.wyb.sseat.service.AdminUserService;
import com.wyb.sseat.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端：用户信息管理接口（路径以 /api/admin 开头，Security 已强制 ROLE_admin）。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    /** 分页 + 条件查询用户列表 */
    @GetMapping
    public Result<Page<UserVO>> page(@RequestParam(defaultValue = "1") long page,
                                     @RequestParam(defaultValue = "10") long size,
                                     @RequestParam(required = false) String keyword,
                                     @RequestParam(required = false) String roleCode,
                                     @RequestParam(required = false) Integer status) {
        return Result.ok(adminUserService.page(page, size, keyword, roleCode, status));
    }

    @PostMapping
    public Result<Void> create(@Valid @RequestBody UserDto.CreateReq req) {
        adminUserService.create(req, CurrentUser.id());
        return Result.ok();
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody UserDto.UpdateReq req) {
        adminUserService.update(req);
        return Result.ok();
    }

    /** 启用/禁用 */
    @PutMapping("/status")
    public Result<Void> status(@Valid @RequestBody UserDto.StatusReq req) {
        adminUserService.changeStatus(req.getId(), req.getStatus(), CurrentUser.id());
        return Result.ok();
    }

    /** 重置密码为 123456 */
    @PutMapping("/{id}/reset-password")
    public Result<Void> resetPassword(@PathVariable Long id) {
        adminUserService.resetPassword(id);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminUserService.delete(id, CurrentUser.id());
        return Result.ok();
    }
}
