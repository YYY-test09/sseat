package com.wyb.sseat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/** 预约相关请求参数 */
public class ReservationDto {

    /** 用户提交预约 */
    @Data
    public static class BookReq {
        @NotNull(message = "请选择自习室")
        private Long roomId;
        @NotNull(message = "请选择日期")
        private LocalDate reserveDate;
        @NotNull(message = "请选择时段")
        @Min(value = 0, message = "时段不合法") @Max(value = 23, message = "时段不合法")
        private Integer startHour;
        @NotNull(message = "请选择座位")
        @Min(value = 1, message = "座位号不合法")
        private Integer seatNo;
    }

    /** 管理端新增/修改预约（处理特殊需求或异常数据修正） */
    @Data
    public static class AdminSaveReq {
        private Long id; // null=新增
        @NotNull(message = "请选择用户")
        private Long userId;
        @NotNull(message = "请选择自习室")
        private Long roomId;
        @NotNull(message = "请选择日期")
        private LocalDate reserveDate;
        @NotNull(message = "请选择时段")
        private Integer startHour;
        @NotNull(message = "请选择座位")
        private Integer seatNo;
        @NotNull(message = "请选择状态")
        private Integer status; // 0待确认 1已确认 2已完成 3已取消
    }

    @Data
    public static class StatusReq {
        @NotNull(message = "缺少id")
        private Long id;
        @NotNull(message = "缺少状态")
        private Integer status;
    }
}
