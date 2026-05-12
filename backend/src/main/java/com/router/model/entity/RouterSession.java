package com.router.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("router_session")
public class RouterSession {

    @TableId(type = IdType.AUTO)
    private Integer id;

    @TableField("session_id")
    private String sessionId;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
