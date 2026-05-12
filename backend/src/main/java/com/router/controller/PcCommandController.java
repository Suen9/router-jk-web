package com.router.controller;

import com.router.common.BaseResponse;
import com.router.common.ResultUtils;
import com.router.model.entity.PcCommand;
import com.router.model.vo.PcCommandVO;
import com.router.service.PcCommandService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PC指令查询 Controller — 提供指令历史、执行结果详情查询
 */
@RestController
@RequestMapping("/api/pc/command")
public class PcCommandController {

    private final PcCommandService pcCommandService;

    public PcCommandController(PcCommandService pcCommandService) {
        this.pcCommandService = pcCommandService;
    }

    /**
     * 获取指令历史列表
     */
    @GetMapping("/list")
    public BaseResponse<Map<String, Object>> list(
            @RequestParam(required = false) Long pcDeviceId,
            @RequestParam(required = false) String commandType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<PcCommandVO> list = pcCommandService.query(pcDeviceId, commandType, status, page, size);
        int total = pcCommandService.count(pcDeviceId, commandType, status);
        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return ResultUtils.success(result);
    }

    /**
     * 获取指令详情
     */
    @GetMapping("/{id}")
    public BaseResponse<PcCommand> detail(@PathVariable Long id) {
        PcCommand cmd = pcCommandService.getById(id);
        if (cmd == null) {
            return ResultUtils.error(40400, "指令不存在");
        }
        return ResultUtils.success(cmd);
    }
}
