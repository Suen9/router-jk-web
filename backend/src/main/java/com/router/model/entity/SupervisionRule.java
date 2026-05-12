package com.router.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("supervision_rule")
public class SupervisionRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String hostname;
    private String mac;

    @TableField("time_slots")
    private String timeSlots;

    @TableField("single_duration")
    private Integer singleDuration;

    @TableField("daily_limit")
    private Integer dailyLimit;

    @TableField("used_today")
    private Integer usedToday;

    @TableField("session_used")
    private Integer sessionUsed;

    @TableField("original_time_slots")
    private String originalTimeSlots;

    @TableField("extend_active")
    private Integer extendActive;

    @TableField("extend_expire_at")
    private LocalDateTime extendExpireAt;

    @TableField("blacklisted_by_supervision")
    private Integer blacklistedBySupervision;

    private String status;
    private String remark;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
