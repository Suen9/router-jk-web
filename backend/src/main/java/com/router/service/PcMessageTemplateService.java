package com.router.service;

import com.router.model.entity.PcMessageTemplate;

import java.util.List;

/**
 * PC弹窗消息模板 Service 接口 — 常用提示词的增删查
 */
public interface PcMessageTemplateService {

    /** 获取所有模板，按 sort_order 升序排列 */
    List<PcMessageTemplate> list();

    /** 新增模板 */
    PcMessageTemplate add(String content);

    /** 删除模板 */
    boolean remove(Long id);
}
