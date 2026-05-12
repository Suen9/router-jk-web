package com.router.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * PC设备展示层VO — 为前端列表页提供格式化后的设备信息，
 * 包含在线状态（根据最后心跳时间实时计算）和客户端版本。
 */
@Data
public class PcDeviceVO {
    private Long id;
    private String hostname;
    private String ip;
    private String mac;
    private String osVersion;
    private String agentVersion;
    private String status;       // online / offline（由 lastHeartbeat 实时计算）
    private LocalDateTime lastHeartbeat;
    private String remark;
    private LocalDateTime createTime;
}
