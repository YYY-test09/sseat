package com.wyb.sseat.controller.admin;

import com.wyb.sseat.common.Result;
import com.wyb.sseat.dto.RoleDto;
import com.wyb.sseat.entity.Role;
import com.wyb.sseat.service.AdminRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 管理端：角色管理接口 */
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
public class AdminRoleController {

    private final AdminRoleService adminRoleService;

    /** 角色列表（数量少，不分页） */
    @GetMapping
    public Result<List<Role>> list() {
        return Result.ok(adminRoleService.list());
    }

    /** 新增/编辑合一：req.id 为空即新增 */
    @PostMapping
    public Result<Void> save(@Valid @RequestBody RoleDto.SaveReq req) {
        adminRoleService.save(req);
        return Result.ok();
    }

    @PutMapping("/status")
    public Result<Void> status(@Valid @RequestBody RoleDto.StatusReq req) {
        adminRoleService.changeStatus(req.getId(), req.getStatus());
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        adminRoleService.delete(id);
        return Result.ok();
    }
}
