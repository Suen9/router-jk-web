package com.router.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.router.common.BaseResponse;
import com.router.common.ResultUtils;
import com.router.model.dto.DeviceDetail;
import com.router.model.dto.TerminalDevice;
import com.router.service.RouterApiService;
import com.router.service.TerminalService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/terminal")
public class TerminalController {

    private final TerminalService terminalService;
    private final RouterApiService routerApi;

    public TerminalController(TerminalService terminalService, RouterApiService routerApi) {
        this.terminalService = terminalService;
        this.routerApi = routerApi;
    }

    @GetMapping("/list")
    public BaseResponse<List<TerminalDevice>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String accessType,
            @RequestParam(required = false) String status) {
        return ResultUtils.success(terminalService.getTerminalList(page, size, keyword, accessType, status));
    }

    @GetMapping("/detail/{idx}")
    public BaseResponse<DeviceDetail> detail(@PathVariable int idx) {
        return ResultUtils.success(terminalService.getDetail(idx));
    }

    @PostMapping("/setAccess")
    public BaseResponse<Boolean> setAccess(@RequestBody Map<String, Object> params) {
        int idx = (int) params.get("idx");
        int internetaccess = (int) params.get("internetaccess");
        boolean ok = terminalService.setAccess(idx, internetaccess);
        if (ok) {
            return ResultUtils.success(true);
        }
        return ResultUtils.error(60000, "操作失败");
    }

    @PostMapping("/setSpeedLimit")
    public BaseResponse<Boolean> setSpeedLimit(@RequestBody Map<String, Object> params) {
        int idx = (int) params.get("idx");
        int usbandwidth = params.get("usbandwidth") != null ? (int) params.get("usbandwidth") : 0;
        int dsbandwidth = params.get("dsbandwidth") != null ? (int) params.get("dsbandwidth") : 0;
        String uploadspeed = (String) params.getOrDefault("uploadspeed", "0.00");
        String downloadspeed = (String) params.getOrDefault("downloadspeed", "0.00");
        boolean ok = terminalService.setSpeedLimit(idx, usbandwidth, dsbandwidth, uploadspeed, downloadspeed);
        if (ok) {
            return ResultUtils.success(true);
        }
        return ResultUtils.error(60000, "操作失败");
    }

    @PostMapping("/blacklist")
    public BaseResponse<Boolean> blacklist(@RequestBody Map<String, String> params) {
        boolean ok = terminalService.addToBlacklist(params.get("mac"), params.get("name"));
        if (ok) {
            return ResultUtils.success(true);
        }
        return ResultUtils.error(60000, "操作失败");
    }

    @PostMapping("/supervise")
    public BaseResponse<Boolean> supervise(@RequestBody Map<String, String> params) {
        terminalService.addToSupervision(params.get("mac"), params.get("hostname"));
        return ResultUtils.success(true);
    }
}
