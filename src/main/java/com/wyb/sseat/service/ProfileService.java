package com.wyb.sseat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wyb.sseat.common.BizException;
import com.wyb.sseat.common.Sha256Util;
import com.wyb.sseat.dto.AuthDto;
import com.wyb.sseat.entity.Role;
import com.wyb.sseat.entity.User;
import com.wyb.sseat.mapper.RoleMapper;
import com.wyb.sseat.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 个人中心：修改资料、修改密码 */
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    /** 更新当前用户的资料字段（只更新非空字段） */
    public void updateProfile(Long userId, User updating) {
        updating.setId(userId);
        updating.setUsername(null);   // 账号不允许改
        updating.setPassword(null);   // 密码走专门的修改密码接口
        updating.setRoleCode(null);   // 角色只能由管理员调整
        updating.setStatus(null);     // 状态只能由管理员调整
        if (updating.getPhone() != null) {
            if (!updating.getPhone().isBlank() && !updating.getPhone().matches("1\\d{10}")) {
                throw new BizException("手机号格式不正确");
            }
            boolean used = userMapper.exists(new LambdaQueryWrapper<User>()
                    .eq(User::getPhone, updating.getPhone()).ne(User::getId, userId));
            if (used) throw new BizException("该手机号已被其他账号绑定");
            if (updating.getPhone().isBlank()) updating.setPhone(null);
        }
        userMapper.updateById(updating);
    }

    /** 修改密码：需验证旧密码（需求明确） */
    public void changePassword(Long userId, AuthDto.ChangePasswordReq req) {
        User user = userMapper.selectById(userId);
        if (!Sha256Util.matches(req.getOldPassword(), user.getPassword())) {
            throw new BizException("旧密码不正确");
        }
        User update = new User();
        update.setId(userId);
        update.setPassword(Sha256Util.hash(req.getNewPassword()));
        userMapper.updateById(update);
    }
}
