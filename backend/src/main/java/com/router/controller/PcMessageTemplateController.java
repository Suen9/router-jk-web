package com.router.controller;

import com.router.common.BaseResponse;
import com.router.common.ResultUtils;
import com.router.model.entity.PcMessageTemplate;
import com.router.service.PcMessageTemplateService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * PC弹窗消息模板 Controller — 预设提示词的增删查
 */
@RestController
@RequestMapping("/api/pc/message-template")
public class PcMessageTemplateController {

    private final PcMessageTemplateService templateService;

    public PcMessageTemplateController(PcMessageTemplateService templateService) {
        this.templateService = templateService;
    }

    /** 获取所有消息模板 */
    @GetMapping("/list")
    public BaseResponse<List<PcMessageTemplate>> list() {
        return ResultUtils.success(templateService.list());
    }

    /** 新增消息模板 */
    @PostMapping("/add")
    public BaseResponse<PcMessageTemplate> add(@RequestBody Map<String, String> params) {
        String content = params.get("content");
        if (content == null || content.isEmpty()) {
            return ResultUtils.error(40000, "消息内容不能为空");
        }
        if (content.length() > 500) {
            return ResultUtils.error(40000, "消息内容不能超过500字");
        }
        return ResultUtils.success(templateService.add(content));
    }

    /** 删除消息模板 */
    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> remove(@PathVariable Long id) {
        boolean ok = templateService.remove(id);
        if (!ok) {
            return ResultUtils.error(40400, "模板不存在");
        }
        return ResultUtils.success(true);
    }
}
