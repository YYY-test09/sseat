package com.wyb.sseat.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 自习室(场地)实体 */
@Data
@TableName("study_room")
public class StudyRoom {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String type;          // 普通自习室/研讨室/电子设备自习室/VIP自习室
    private String description;
    private BigDecimal price;     // 每小时价格
    private BigDecimal discount;  // 0.01~1，如 0.8 表示八折
    private Integer seatCount;    // 总座位数
    private Integer maxCapacity;  // 每时段最大预约人数
    private String facilities;    // 逗号分隔：空调,电源,WiFi,白板
    private String coverImg;
    private Integer openStart;    // 开放起始整点，如 8
    private Integer openEnd;      // 开放结束整点，如 21
    private Integer likeCount;    // 点赞数（点赞/取消时同步增减）
    private Integer status;       // 0停用 1开放预约
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 非数据库字段：前台列表实时计算的"今日剩余名额"（时段余量最小值） */
    @TableField(exist = false)
    private Integer remaining;
}
