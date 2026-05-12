package com.router.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * PC指令展示层VO — 为前端指令历史页提供格式化后的指令信息，
 * 包含关联的PC设备名和格式化后的执行结果摘要。
 */
@Data
public class PcCommandVO {
    private Long id;
    private Long pcDeviceId;
    private String deviceHostname;
    private String commandType;
    private String params;
    private String status;
    private String result;
    private String errorMessage;
    private String createdBy;
    private LocalDateTime createTime;
    private LocalDateTime sendTime;
    private LocalDateTime completeTime;
}
