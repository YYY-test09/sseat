package com.wyb.sseat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 预约记录实体。
 * 时段 = startHour ~ startHour+1 的整点段（如 8 表示 08:00-09:00）。
 * 取消预约时 seatNo 置 null 以释放座位（唯一索引允许多个 NULL）。
 */
@Data
@TableName("reservation")
public class Reservation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long roomId;
    private LocalDate reserveDate;
    private Integer startHour;
    private Integer seatNo;
    private Integer status; // 0待确认 1已确认 2已完成 3已取消
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
