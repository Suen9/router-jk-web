package com.router.controller;

import com.router.common.BaseResponse;
import com.router.common.ResultUtils;
import com.router.model.entity.ConnectionLog;
import com.router.service.LogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class LogController {

    private final LogService logService;

    public LogController(LogService logService) {
        this.logService = logService;
    }

    @GetMapping("/list")
    public BaseResponse<List<ConnectionLog>> list(
            @RequestParam(required = false) String device,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String timeRange,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResultUtils.success(logService.query(device, type, timeRange, status, source, page, size));
    }

    @PutMapping("/{id}/status")
    public BaseResponse<Boolean> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> params) {
        return ResultUtils.success(logService.updateStatus(id,
                params.get("status"), params.get("remark")));
    }

    @GetMapping("/export")
    public BaseResponse<List<ConnectionLog>> export() {
        return ResultUtils.success(logService.getTodayLogs());
    }
}
