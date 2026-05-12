package com.router.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("connection_log")
public class ConnectionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("device_name")
    private String deviceName;

    private String mac;

    @TableField("connect_time")
    private LocalDateTime connectTime;

    @TableField("disconnect_time")
    private LocalDateTime disconnectTime;

    private Integer duration;

    @TableField("connect_count")
    private Integer connectCount;

    @TableField("log_type")
    private String logType;

    private String status;
    private String source;
    private String remark;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
