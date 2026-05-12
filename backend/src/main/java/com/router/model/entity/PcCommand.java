package com.router.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * PC指令实体类 — 记录发送给PC Agent的控制指令，包含指令类型、参数、
 * 执行状态、结果和执行时间。支持进程列表、锁屏、关机等操作。
 */
@Data
@TableName("pc_command")
public class PcCommand {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("pc_device_id")
    private Long pcDeviceId;

    @TableField("command_type")
    private String commandType;

    private String params;
    private String status;
    private String result;

    @TableField("error_message")
    private String errorMessage;

    @TableField("created_by")
    private String createdBy;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField("send_time")
    private LocalDateTime sendTime;

    @TableField("complete_time")
    private LocalDateTime completeTime;
}
