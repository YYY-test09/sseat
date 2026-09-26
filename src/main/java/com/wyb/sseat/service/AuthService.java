package com.wyb.sseat.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wyb.sseat.common.BizException;
import com.wyb.sseat.common.JwtUtil;
import com.wyb.sseat.common.Sha256Util;
import com.wyb.sseat.dto.AuthDto;
import com.wyb.sseat.entity.Role;
import com.wyb.sseat.entity.User;
import com.wyb.sseat.mapper.RoleMapper;
import com.wyb.sseat.mapper.UserMapper;
import com.wyb.sseat.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证业务：注册、登录、短信登录、找回密码。
 * 密码一律 SHA-256 加密存库；登录成功签发 JWT 令牌。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final JwtUtil jwtUtil;
    private final CaptchaService captchaService;
    private final SmsService smsService;

    /** 登录返回体：{ token, user } */
    private Map<String, Object> issueToken(User user) {
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException("账号已被禁用，请联系管理员");
        }
        Role role = findRole(user.getRoleCode());
        if (role == null || role.getStatus() != 1) {
            throw new BizException("账号角色异常，请联系管理员");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("token", jwtUtil.createToken(user.getId(), user.getRoleCode()));
        result.put("user", UserVO.of(user, role));
        return result;
    }

    /** 账号密码登录：先验图形验证码，再比对密码 */
    public Map<String, Object> login(AuthDto.LoginReq req) {
        captchaService.verify(req.getCaptchaKey(), req.getCaptchaCode());
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (user == null || !Sha256Util.matches(req.getPassword(), user.getPassword())) {
            throw new BizException("账号或密码错误");
        }
        return issueToken(user);
    }

    /** 账号密码注册：新账号默认普通用户角色 */
    public void register(AuthDto.RegisterReq req) {
        captchaService.verify(req.getCaptchaKey(), req.getCaptchaCode());
        checkUsernameAvailable(req.getUsername());
        insertUser(req.getUsername(), req.getPassword(),
                req.getNickname() == null || req.getNickname().isBlank() ? req.getUsername() : req.getNickname(),
                null);
    }

    /** 手机号+短信码注册 */
    public void smsRegister(AuthDto.SmsRegisterReq req) {
        smsService.verify(req.getPhone(), req.getSmsCode());
        checkUsernameAvailable(req.getUsername());
        insertUser(req.getUsername(), req.getPassword(), req.getUsername(), req.getPhone());
    }

    /** 手机号+短信码登录：验证码即凭证，不再需要密码 */
    public Map<String, Object> smsLogin(AuthDto.SmsLoginReq req) {
        smsService.verify(req.getPhone(), req.getSmsCode());
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, req.getPhone()));
        if (user == null) {
            throw new BizException("该手机号尚未注册");
        }
        return issueToken(user);
    }

    /** 找回密码：验证手机号+短信码后重置 */
    public void forgotPassword(AuthDto.ForgotReq req) {
        smsService.verify(req.getPhone(), req.getSmsCode());
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhone, req.getPhone()));
        if (user == null) {
            throw new BizException("该手机号尚未注册");
        }
        User update = new User();
        update.setId(user.getId());
        update.setPassword(Sha256Util.hash(req.getNewPassword()));
        userMapper.updateById(update);
    }

    /** 当前登录用户信息（页面刷新时重新拉取） */
    public UserVO me(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return UserVO.of(user, findRole(user.getRoleCode()));
    }

    // ---------- 私有小方法 ----------

    private Role findRole(String roleCode) {
        return roleMapper.selectOne(new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, roleCode));
    }

    private void checkUsernameAvailable(String username) {
        if (userMapper.exists(new LambdaQueryWrapper<User>().eq(User::getUsername, username))) {
            throw new BizException("该账号已被注册");
        }
    }

    private void insertUser(String username, String password, String nickname, String phone) {
        User user = new User();
        user.setUsername(username);
        user.setPassword(Sha256Util.hash(password));
        user.setNickname(nickname);
        user.setPhone(phone);
        user.setGender(0);
        user.setRoleCode("user"); // 注册的永远是普通用户，管理员只能由后台创建
        user.setStatus(1);
        userMapper.insert(user);
    }
}
