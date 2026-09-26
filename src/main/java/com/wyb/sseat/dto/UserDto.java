package com.wyb.sseat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 用户管理相关的请求参数 */
public class UserDto {

    /** 管理端新增用户 */
    @Data
    public static class CreateReq {
        @NotBlank(message = "请输入账号")
        private String username;
        @NotBlank(message = "请输入初始密码")
        private String password;
        private String nickname;
        private String phone;
        private String email;
        private Integer gender;
        @NotBlank(message = "请选择角色")
        private String roleCode;
    }

    /** 管理端编辑用户资料/角色 */
    @Data
    public static class UpdateReq {
        @NotNull(message = "缺少用户id")
        private Long id;
        private String nickname;
        private String phone;
        private String email;
        private Integer gender;
        private String address;
        private String intro;
        private String avatar;
        private String roleCode; // 管理端可调整角色归属；个人中心编辑时不传
    }

    /** 启用/禁用 */
    @Data
    public static class StatusReq {
        @NotNull(message = "缺少id")
        private Long id;
        @NotNull(message = "缺少状态值")
        private Integer status;
    }
}
