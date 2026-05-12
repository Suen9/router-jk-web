package com.router.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.router.mapper.ConnectionLogMapper;
import com.router.model.entity.ConnectionLog;
import com.router.service.LogService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
public class LogServiceImpl implements LogService {

    private final ConnectionLogMapper mapper;

    public LogServiceImpl(ConnectionLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(ConnectionLog log) {
        mapper.insert(log);
    }

    @Override
    public List<ConnectionLog> query(String device, String type, String timeRange,
                                      String status, String source, int page, int size) {
        QueryWrapper<ConnectionLog> qw = new QueryWrapper<>();
        if (device != null && !device.isEmpty()) {
            qw.and(w -> w.like("device_name", device).or().like("mac", device));
        }
        if (type != null && !type.isEmpty() && !"全部".equals(type)) {
            qw.eq("log_type", mapLogType(type));
        }
        if (status != null && !status.isEmpty() && !"全部".equals(status)) {
            qw.eq("status", mapStatus(status));
        }
        if (source != null && !source.isEmpty() && !"全部".equals(source)) {
            qw.eq("source", mapSource(source));
        }
        qw.orderByDesc("create_time");

        Page<ConnectionLog> p = new Page<>(page, size);
        Page<ConnectionLog> result = mapper.selectPage(p, qw);
        return result.getRecords();
    }

    @Override
    public boolean updateStatus(Long id, String status, String remark) {
        ConnectionLog log = mapper.selectById(id);
        if (log == null) return false;
        log.setStatus(status);
        log.setRemark(remark);
        return mapper.updateById(log) > 0;
    }

    @Override
    public List<ConnectionLog> getTodayLogs() {
        QueryWrapper<ConnectionLog> qw = new QueryWrapper<>();
        qw.ge("create_time", LocalDateTime.of(LocalDate.now(), LocalTime.MIN));
        qw.orderByDesc("create_time");
        return mapper.selectList(qw);
    }

    @Override
    public int countTodayAlerts() {
        QueryWrapper<ConnectionLog> qw = new QueryWrapper<>();
        qw.ge("create_time", LocalDateTime.of(LocalDate.now(), LocalTime.MIN));
        qw.in("log_type", "abnormal", "timeout", "blacklist", "whitelist_violation");
        return mapper.selectCount(qw).intValue();
    }

    private String mapLogType(String type) {
        switch (type) {
            case "普通连接": return "normal";
            case "异常连接": return "abnormal";
            case "超时告警": return "timeout";
            case "黑名单触发": return "blacklist";
            case "白名单外设备": case "白名单外异常": return "whitelist_violation";
            default: return type;
        }
    }

    private String mapStatus(String s) {
        switch (s) {
            case "未处理": return "pending";
            case "已处理": return "processed";
            case "忽略": return "ignored";
            default: return s;
        }
    }

    private String mapSource(String s) {
        switch (s) {
            case "监管设备": return "supervision";
            case "白名单外设备": return "whitelist";
            case "黑名单触发": return "blacklist";
            default: return s;
        }
    }
}
