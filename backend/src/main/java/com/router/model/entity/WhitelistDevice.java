package com.router.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("whitelist_device")
public class WhitelistDevice {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String hostname;
    private String mac;
    private String remark;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
