package com.wyb.sseat.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import com.wyb.sseat.common.CurrentUser;
import com.wyb.sseat.common.Result;
import com.wyb.sseat.dto.AuthDto;
import com.wyb.sseat.service.AuthService;
import com.wyb.sseat.service.CaptchaService;
import com.wyb.sseat.service.SmsService;
import com.wyb.sseat.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 认证接口（免登录白名单，路径必须放在 /api/auth/ 下）：
 * 图形验证码、注册、登录、短信、找回密码。
 */
@Tag(name="01-认证(登录/注册/验证码)")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CaptchaService captchaService;
    private final SmsService smsService;

    /** 获取图形验证码：返回 { captchaKey, imgBase64 }，前端把 imgBase64 放进 <img :src> */
    @GetMapping("/captcha")
    public Result<CaptchaService.CaptchaImage> captcha(
            @Value("${sseat.captcha.debug-reveal:false}") boolean reveal) {
        return Result.ok(captchaService.generate(reveal));
    }

    /** 发送短信验证码（模拟）：mock 模式下前端会收到提示码，方便演示 */
    @PostMapping("/sms/send")
    public Result<Map<String, Object>> sendSms(@Valid @RequestBody AuthDto.SmsSendReq req) {
        smsService.send(req.getPhone());
        return Result.ok(Map.of(
                "mock", true,
                "tip", "当前为模拟短信模式，验证码为 8888"));
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody AuthDto.RegisterReq req) {
        authService.register(req);
        return Result.ok();
    }

    @PostMapping("/sms-register")
    public Result<Void> smsRegister(@Valid @RequestBody AuthDto.SmsRegisterReq req) {
        authService.smsRegister(req);
        return Result.ok();
    }

    /** 登录成功返回 { token, user }，前端保存 token 后访问其他接口 */
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody AuthDto.LoginReq req) {
        return Result.ok(authService.login(req));
    }

    @PostMapping("/sms-login")
    public Result<Map<String, Object>> smsLogin(@Valid @RequestBody AuthDto.SmsLoginReq req) {
        return Result.ok(authService.smsLogin(req));
    }

    @PostMapping("/forgot")
    public Result<Void> forgot(@Valid @RequestBody AuthDto.ForgotReq req) {
        authService.forgotPassword(req);
        return Result.ok();
    }

    /** 查询当前登录用户（需要携带令牌） */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(authService.me(CurrentUser.id()));
    }
}
