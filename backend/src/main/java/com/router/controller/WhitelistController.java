package com.router.controller;

import com.router.common.BaseResponse;
import com.router.common.ResultUtils;
import com.router.model.entity.WhitelistDevice;
import com.router.service.WhitelistService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/whitelist")
public class WhitelistController {

    private final WhitelistService whitelistService;

    public WhitelistController(WhitelistService whitelistService) {
        this.whitelistService = whitelistService;
    }

    @GetMapping("/list")
    public BaseResponse<List<WhitelistDevice>> list() {
        return ResultUtils.success(whitelistService.list());
    }

    @PostMapping("/add")
    public BaseResponse<WhitelistDevice> add(@RequestBody WhitelistDevice device) {
        return ResultUtils.success(whitelistService.add(device));
    }

    @DeleteMapping("/{id}")
    public BaseResponse<Boolean> remove(@PathVariable Long id) {
        return ResultUtils.success(whitelistService.remove(id));
    }
}
