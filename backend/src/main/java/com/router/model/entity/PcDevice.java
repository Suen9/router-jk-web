package com.router.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * PC设备实体类 — 记录被管控的PC设备信息，包含主机名、IP、MAC、操作系统版本、
 * 客户端版本、在线状态、Agent认证令牌和最后心跳时间。
 */
@Data
@TableName("pc_device")
public class PcDevice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String hostname;
    private String ip;
    private String mac;

    @TableField("os_version")
    private String osVersion;

    @TableField("agent_version")
    private String agentVersion;

    private String status;

    @TableField("agent_token")
    private String agentToken;

    @TableField("last_heartbeat")
    private LocalDateTime lastHeartbeat;

    private String remark;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
