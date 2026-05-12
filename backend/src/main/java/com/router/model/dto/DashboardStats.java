package com.router.model.dto;

import lombok.Data;

@Data
public class DashboardStats {
    private int onlineDevices;
    private int blacklistCount;
    private int supervisionCount;
    private int todayAlerts;
    private String sessionStatus;
    private String routerAddress;
    private String sessionId;
}
