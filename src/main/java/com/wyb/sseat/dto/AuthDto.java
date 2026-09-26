package com.wyb.sseat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录/注册等接口的请求参数载体（DTO = Data Transfer Object）。
 * @NotBlank/@Size 等注解配合 Controller 方法的 @Valid 生效，
 * 不合法时 GlobalExceptionHandler 会把提示语返回给前端。
 */
public class AuthDto {

    /** 账号密码登录 */
    @Data
    public static class LoginReq {
        @NotBlank(message = "请输入账号")
        private String username;
        @NotBlank(message = "请输入密码")
        private String password;
        @NotBlank(message = "请输入图形验证码")
        private String captchaKey;
        @NotBlank(message = "请输入图形验证码")
        private String captchaCode;
    }

    /** 账号密码注册 */
    @Data
    public static class RegisterReq {
        @NotBlank(message = "请输入账号")
        @Size(min = 4, max = 20, message = "账号长度需 4~20 位")
        @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "账号只能包含字母、数字、下划线")
        private String username;
        @NotBlank(message = "请输入密码")
        @Size(min = 6, max = 20, message = "密码长度需 6~20 位")
        private String password;
        @NotBlank(message = "请输入图形验证码")
        private String captchaKey;
        @NotBlank(message = "请输入图形验证码")
        private String captchaCode;
        private String nickname;
    }

    /** 手机号 + 短信验证码 注册 */
    @Data
    public static class SmsRegisterReq {
        @NotBlank(message = "请输入手机号")
        @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
        private String phone;
        @NotBlank(message = "请输入短信验证码")
        private String smsCode;
        @NotBlank(message = "请输入账号")
        @Size(min = 4, max = 20, message = "账号长度需 4~20 位")
        private String username;
        @NotBlank(message = "请输入密码")
        @Size(min = 6, max = 20, message = "密码长度需 6~20 位")
        private String password;
    }

    /** 手机号 + 短信验证码 登录 */
    @Data
    public static class SmsLoginReq {
        @NotBlank(message = "请输入手机号")
        @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
        private String phone;
        @NotBlank(message = "请输入短信验证码")
        private String smsCode;
    }

    /** 发送短信验证码 */
    @Data
    public static class SmsSendReq {
        @NotBlank(message = "请输入手机号")
        @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
        private String phone;
    }

    /** 找回密码（手机号+短信码验证后直接重置） */
    @Data
    public static class ForgotReq {
        @NotBlank(message = "请输入手机号")
        @Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
        private String phone;
        @NotBlank(message = "请输入短信验证码")
        private String smsCode;
        @NotBlank(message = "请输入新密码")
        @Size(min = 6, max = 20, message = "密码长度需 6~20 位")
        private String newPassword;
    }

    /** 修改密码（需验证旧密码） */
    @Data
    public static class ChangePasswordReq {
        @NotBlank(message = "请输入旧密码")
        private String oldPassword;
        @NotBlank(message = "请输入新密码")
        @Size(min = 6, max = 20, message = "密码长度需 6~20 位")
        private String newPassword;
    }
}
