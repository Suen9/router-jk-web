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
}
