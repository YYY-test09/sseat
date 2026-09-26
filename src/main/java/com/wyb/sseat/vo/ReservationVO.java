package com.wyb.sseat.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 预约记录视图：在预约字段基础上补充自习室名、类型、用户名，供列表展示 */
@Data
public class ReservationVO {
    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private Long roomId;
    private String roomName;
    private String roomType;
    private LocalDate reserveDate;
    private Integer startHour;
    private Integer seatNo;
    private Integer status; // 0待确认 1已确认 2已完成 3已取消
    private LocalDateTime createTime;

    /** 供前端展示的时段文字，如 08:00-09:00 */
    public String getTimeSlot() {
        if (startHour == null) return "";
        return String.format("%02d:00-%02d:00", startHour, startHour + 1);
    }
}
