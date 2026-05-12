package com.router.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.router.model.entity.ConnectionLog;

import java.util.List;
import java.util.Map;

public interface LogService {
    void save(ConnectionLog log);
    List<ConnectionLog> query(String device, String type, String timeRange, String status, String source, int page, int size);
    boolean updateStatus(Long id, String status, String remark);
    List<ConnectionLog> getTodayLogs();
    int countTodayAlerts();
}
