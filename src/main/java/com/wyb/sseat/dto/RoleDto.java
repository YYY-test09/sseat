package com.wyb.sseat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 角色管理请求参数 */
public class RoleDto {

    @Data
    public static class SaveReq {
        private Long id; // null=新增，非null=编辑（故不能加 @NotNull）
        @NotBlank(message = "请输入角色名称")
        private String roleName;
        private String roleCode; // 新增必填；编辑内置角色时忽略该字段
        private String menus;
        private String remark;
    }

    @Data
    public static class StatusReq {
        @NotNull(message = "缺少id")
        private Long id;
        @NotNull(message = "缺少状态值")
        private Integer status;
    }
}
