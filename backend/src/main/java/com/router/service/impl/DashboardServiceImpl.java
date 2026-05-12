package com.router.service.impl;

import com.router.config.RouterConfig;
import com.router.mapper.SupervisionRuleMapper;
import com.router.model.dto.DashboardStats;
import com.router.service.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final TerminalService terminalService;
    private final BlacklistService blacklistService;
    private final SupervisionRuleMapper supervisionRuleMapper;
    private final LogService logService;
    private final RouterConfig routerConfig;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SESSION_REDIS_KEY = "router:session_id";

    public DashboardServiceImpl(TerminalService terminalService, BlacklistService blacklistService,
                                 SupervisionRuleMapper supervisionRuleMapper, LogService logService,
                                 RouterConfig routerConfig, RedisTemplate<String, Object> redisTemplate) {
        this.terminalService = terminalService;
        this.blacklistService = blacklistService;
        this.supervisionRuleMapper = supervisionRuleMapper;
        this.logService = logService;
        this.routerConfig = routerConfig;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public DashboardStats getStats() {
        DashboardStats stats = new DashboardStats();
        stats.setOnlineDevices((int) terminalService.getTerminalList(1, 100, null, null, null)
                .stream().filter(d -> d.getActive() == 1).count());
        stats.setBlacklistCount(blacklistService.getBlacklist().size());
        stats.setSupervisionCount(supervisionRuleMapper.selectCount(null).intValue());
        stats.setTodayAlerts(logService.countTodayAlerts());
        stats.setRouterAddress(routerConfig.getBaseUrl());

        String sessionId = (String) redisTemplate.opsForValue().get(SESSION_REDIS_KEY);
        stats.setSessionStatus(sessionId != null ? "正常" : "未登录");
        if (sessionId != null && sessionId.length() > 20) {
            stats.setSessionId(sessionId.substring(0, 8) + "..." + sessionId.substring(sessionId.length() - 8));
        } else {
            stats.setSessionId(sessionId);
        }
        return stats;
    }
}
