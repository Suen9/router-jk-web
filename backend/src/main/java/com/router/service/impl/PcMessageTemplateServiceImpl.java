package com.router.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.router.mapper.PcMessageTemplateMapper;
import com.router.model.entity.PcMessageTemplate;
import com.router.service.PcMessageTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * PC弹窗消息模板服务实现
 */
@Service
public class PcMessageTemplateServiceImpl implements PcMessageTemplateService {

    private static final Logger log = LoggerFactory.getLogger(PcMessageTemplateServiceImpl.class);

    private final PcMessageTemplateMapper mapper;

    public PcMessageTemplateServiceImpl(PcMessageTemplateMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<PcMessageTemplate> list() {
        QueryWrapper<PcMessageTemplate> qw = new QueryWrapper<>();
        qw.orderByAsc("sort_order");
        return mapper.selectList(qw);
    }

    @Override
    public PcMessageTemplate add(String content) {
        // 获取当前最大 sort_order
        QueryWrapper<PcMessageTemplate> qw = new QueryWrapper<>();
        qw.orderByDesc("sort_order").last("LIMIT 1");
        PcMessageTemplate last = mapper.selectOne(qw);
        int nextOrder = (last != null && last.getSortOrder() != null) ? last.getSortOrder() + 1 : 0;

        PcMessageTemplate tpl = new PcMessageTemplate();
        tpl.setContent(content);
        tpl.setSortOrder(nextOrder);
        mapper.insert(tpl);
        log.info("[消息模板] 新增 id={} content={}", tpl.getId(), content);
        return tpl;
    }

    @Override
    public boolean remove(Long id) {
        int rows = mapper.deleteById(id);
        if (rows > 0) {
            log.info("[消息模板] 删除 id={}", id);
        }
        return rows > 0;
    }
}
