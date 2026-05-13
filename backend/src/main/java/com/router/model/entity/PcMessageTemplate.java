package com.router.model.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * PC弹窗消息模板 — 预设常用提示词，供 Web 端选择下发给 PC Agent 弹窗显示
 */
@Data
@TableName("pc_message_template")
public class PcMessageTemplate {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 消息内容 */
    private String content;

    /** 排序号 */
    private Integer sortOrder;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
