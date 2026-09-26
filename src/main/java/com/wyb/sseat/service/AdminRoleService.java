package com.wyb.sseat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wyb.sseat.common.BizException;
import com.wyb.sseat.dto.RoleDto;
import com.wyb.sseat.entity.Role;
import com.wyb.sseat.entity.User;
import com.wyb.sseat.mapper.RoleMapper;
import com.wyb.sseat.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 管理端：角色管理。
 * 保护规则（与需求对应）：超级管理员角色不可删除；内置角色编码不可改；
 * 名称与编码全局唯一；角色下有用户时不可删除。
 */
@Service
@RequiredArgsConstructor
public class AdminRoleService {

    /** 内置角色编码：不允许删除、不允许改编码 */
    private static final List<String> BUILTIN = List.of("admin", "user");

    private final RoleMapper roleMapper;
    private final UserMapper userMapper;

    public List<Role> list() {
        return roleMapper.selectList(new LambdaQueryWrapper<Role>().orderByAsc(Role::getId));
    }

    public void save(RoleDto.SaveReq req) {
        // 名称唯一校验（排除自己）
        boolean nameUsed = roleMapper.exists(new LambdaQueryWrapper<Role>()
                .eq(Role::getRoleName, req.getRoleName())
                .ne(req.getId() != null, Role::getId, req.getId()));
        if (nameUsed) throw new BizException("角色名称已存在");

        if (req.getId() == null) {
            // 新增：编码必填且唯一
            if (req.getRoleCode() == null || req.getRoleCode().isBlank()) {
                throw new BizException("请输入角色编码");
            }
            if (roleMapper.exists(new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, req.getRoleCode()))) {
                throw new BizException("角色编码已存在");
            }
            Role role = new Role();
            role.setRoleName(req.getRoleName());
            role.setRoleCode(req.getRoleCode());
            role.setMenus(req.getMenus() == null ? "" : req.getMenus());
            role.setRemark(req.getRemark());
            role.setStatus(1);
            roleMapper.insert(role);
        } else {
            Role exist = mustExist(req.getId());
            if (BUILTIN.contains(exist.getRoleCode())
                    && req.getRoleCode() != null && !req.getRoleCode().isBlank()
                    && !req.getRoleCode().equals(exist.getRoleCode())) {
                throw new BizException("内置角色的编码不允许修改");
            }
            Role r = new Role();
            r.setId(req.getId());
            r.setRoleName(req.getRoleName());
            r.setMenus(req.getMenus() == null ? "" : req.getMenus());
            r.setRemark(req.getRemark());
            roleMapper.updateById(r);
        }
    }

    public void changeStatus(Long id, Integer status) {
        Role exist = mustExist(id);
        if ("admin".equals(exist.getRoleCode()) && status == 0) {
            throw new BizException("超级管理员角色不可禁用");
        }
        Role r = new Role();
        r.setId(id);
        r.setStatus(status);
        roleMapper.updateById(r);
        // 禁用角色后，该角色下所有用户的令牌在下次请求时立即失效（过滤器查库核对角色状态）
    }

    public void delete(Long id) {
        Role exist = mustExist(id);
        if (BUILTIN.contains(exist.getRoleCode())) {
            throw new BizException("内置角色不可删除");
        }
        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getRoleCode, exist.getRoleCode()))) {
            throw new BizException("该角色下仍有用户，请先调整用户角色");
        }
        roleMapper.deleteById(id);
    }

    private Role mustExist(Long id) {
        Role r = roleMapper.selectById(id);
        if (r == null) throw new BizException("角色不存在");
        return r;
    }
}
