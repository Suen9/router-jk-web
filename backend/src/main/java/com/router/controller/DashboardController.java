package com.router.controller;

import com.router.common.BaseResponse;
import com.router.common.ResultUtils;
import com.router.model.dto.DashboardStats;
import com.router.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/stats")
    public BaseResponse<DashboardStats> stats() {
        return ResultUtils.success(dashboardService.getStats());
    }
}
