package com.router.model.dto;

import lombok.Data;

@Data
public class SupervisionVO {
    private Long id;
    private String hostname;
    private String mac;
    private String timeSlots;
    private Integer singleDuration;
    private Integer dailyLimit;
    private Integer usedToday;
    private Integer remaining;
    private Integer blacklistedBySupervision;
    private String status;
    private String remark;
    private Integer extendActive;
    private Integer extendExpireMinutes; // 延时剩余分钟数，前端展示用
    /** 若规则 MAC 匹配 pc_device，填充以下 PC 相关字段 */
    private String pcHostname;
    private Integer pcOnline; // 0/1
    private Integer pcUsedTodayMinutes; // PC 今日已用分钟数（与 usedToday 一致）
}
