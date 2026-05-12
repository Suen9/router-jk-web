package com.router.controller;

import com.router.common.BaseResponse;
import com.router.common.ResultUtils;
import com.router.model.dto.BlacklistItem;
import com.router.service.BlacklistService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/blacklist")
public class BlacklistController {

    private final BlacklistService blacklistService;

    public BlacklistController(BlacklistService blacklistService) {
        this.blacklistService = blacklistService;
    }

    @GetMapping("/list")
    public BaseResponse<List<BlacklistItem>> list() {
        return ResultUtils.success(blacklistService.getBlacklist());
    }

    @PostMapping("/add")
    public BaseResponse<Boolean> add(@RequestBody Map<String, String> params) {
        boolean ok = blacklistService.add(params.get("mac"), params.get("name"));
        if (ok) {
            return ResultUtils.success(true);
        }
        return ResultUtils.error(60000, "添加失败");
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> delete(@RequestBody Map<String, Object> params) {
        @SuppressWarnings("unchecked")
        List<String> macList = (List<String>) params.get("macList");
        boolean ok = blacklistService.delete(macList);
        if (ok) {
            return ResultUtils.success(true);
        }
        return ResultUtils.error(60000, "删除失败");
    }
}
