package com.router.scheduler;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.router.model.dto.TerminalDevice;
import com.router.model.entity.ConnectionLog;
import com.router.model.entity.PcDevice;
import com.router.model.entity.SupervisionRule;
import com.router.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MonitorScheduler {

    private static final Logger log = LoggerFactory.getLogger(MonitorScheduler.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int TICK_SECONDS = 10; // scheduler interval

    // Track last reset date to avoid repeated midnight resets
    private LocalDate lastResetDate = LocalDate.now();

    /** 跟踪已发送 5 分钟预警的 PC 规则 ID，避免重复发送 */
    private final Set<Long> warnedPcRules = ConcurrentHashMap.newKeySet();

    private final TerminalService terminalService;
    private final SupervisionService supervisionService;
    private final WhitelistService whitelistService;
    private final LogService logService;
    private final BlacklistService blacklistService;
    private final PcDeviceService pcDeviceService;
    private final PcCommandService pcCommandService;

    public MonitorScheduler(TerminalService terminalService, SupervisionService supervisionService,
                             WhitelistService whitelistService, LogService logService,
                             BlacklistService blacklistService,
                             PcDeviceService pcDeviceService, PcCommandService pcCommandService) {
        this.terminalService = terminalService;
        this.supervisionService = supervisionService;
        this.whitelistService = whitelistService;
        this.logService = logService;
        this.blacklistService = blacklistService;
        this.pcDeviceService = pcDeviceService;
        this.pcCommandService = pcCommandService;
    }

    @Scheduled(fixedDelay = TICK_SECONDS * 1000)
    public void monitor() {
        // Midnight reset: revert extensions, clear daily counters
        LocalDate today = LocalDate.now();
        if (!today.equals(lastResetDate)) {
            log.info("===== 午夜重置: 还原临时延时 + 清空 usedToday/sessionUsed =====");
            // Revert any temporary time extensions first
            supervisionService.revertExtensions();
            // Reset daily counters for all active rules
            List<SupervisionRule> allRules = supervisionService.getActiveRules();
            for (SupervisionRule r : allRules) {
                supervisionService.updateUsage(r.getId(), 0, 0, r.getBlacklistedBySupervision() != null ? r.getBlacklistedBySupervision() : 0);
            }
            lastResetDate = today;
        }

        log.info("========== 监管巡检开始 ==========");
        try {
            // Check for expired extension timers before normal processing
            int expiredCount = supervisionService.checkExtendExpiry();
            if (expiredCount > 0) {
                log.info("延时到期黑名单处理: {} 台", expiredCount);
            }

            List<TerminalDevice> devices = terminalService.getTerminalList(1, 100, null, null, null);
            List<SupervisionRule> rules = supervisionService.getActiveRules();

            long onlineCount = devices.stream().filter(d -> d.getActive() == 1).count();
            log.info("当前在线终端: {} 台, 活跃监管规则: {} 条", onlineCount, rules.size());

            int blacklistCount = 0;
            int unblockCount = 0;
            int accumulatedCount = 0;
            int extendedCount = 0;
            int pcLockCount = 0;
            int pcUnlockCount = 0;
            int pcAccumulatedCount = 0;
            int pcExtendedCount = 0;

            for (SupervisionRule rule : rules) {
                try {
                    if (isPcRule(rule.getMac())) {
                        // PC 监管规则：通过 Agent 指令执行
                        String result = processPcRule(rule);
                        if ("locked".equals(result)) pcLockCount++;
                        else if ("unlocked".equals(result)) pcUnlockCount++;
                        if ("accumulated".equals(result)) pcAccumulatedCount++;
                        if ("extended".equals(result)) pcExtendedCount++;
                    } else {
                        // 终端监管规则：通过路由器黑名单执行
                        String result = processRule(rule, devices);
                        if ("blacklisted".equals(result)) blacklistCount++;
                        else if ("unblocked".equals(result)) unblockCount++;
                        if ("accumulated".equals(result)) accumulatedCount++;
                        if ("extended".equals(result)) extendedCount++;
                    }
                } catch (Exception e) {
                    log.error("处理监管规则异常 mac={}: {}", rule.getMac(), e.getMessage());
                }
            }

            log.info("监管巡检完成: [终端]累积{}台 延时{}台 拉黑{}台 解除{}台 | [PC]累积{}台 延时{}台 锁屏{}台 解锁{}台",
                    accumulatedCount, extendedCount, blacklistCount, unblockCount,
                    pcAccumulatedCount, pcExtendedCount, pcLockCount, pcUnlockCount);

            for (TerminalDevice device : devices) {
                String mac = device.getMac();
                if (mac == null || mac.isEmpty()) continue;
                if (!whitelistService.isInWhitelist(mac)) {
                    checkWhitelistViolation(device);
                }
            }
        } catch (Exception e) {
            log.error("监管巡检异常: {}", e.getMessage(), e);
        }
        log.info("========== 监管巡检结束 ==========");
    }

    private String processRule(SupervisionRule rule, List<TerminalDevice> devices) {
        String mac = rule.getMac();
        if (mac == null || mac.isEmpty()) return "skipped";

        // Extension mode: timer is running — skip all supervision, just track usage
        int extendActive = rule.getExtendActive() != null ? rule.getExtendActive() : 0;
        if (extendActive == 1) {
            TerminalDevice device = devices.stream()
                    .filter(d -> d.getMac() != null && d.getMac().equalsIgnoreCase(mac))
                    .findFirst().orElse(null);
            boolean isOnline = device != null && device.getActive() == 1;
            int usedTodaySeconds = rule.getUsedToday() != null ? rule.getUsedToday() : 0;

            if (isOnline) {
                usedTodaySeconds += TICK_SECONDS;
                log.info("[{}] 延时模式中(到期{}), 跳过监管巡视, 仅累积时长 → 今日已用{}秒",
                        rule.getHostname(), rule.getExtendExpireAt(), usedTodaySeconds);
            } else {
                log.info("[{}] 延时模式中(到期{}), 设备离线, 跳过监管巡视",
                        rule.getHostname(), rule.getExtendExpireAt());
            }

            supervisionService.updateUsage(rule.getId(), usedTodaySeconds, 0, 0);
            return "extended";
        }

        TerminalDevice device = devices.stream()
                .filter(d -> d.getMac() != null && d.getMac().equalsIgnoreCase(mac))
                .findFirst().orElse(null);

        boolean isOnline = device != null && device.getActive() == 1;
        int routerOnlineSeconds = isOnline ? device.getOnlinetime() : 0;

        int currentSlotIdx = getCurrentSlotIndex(rule.getTimeSlots());
        boolean inTimeSlot = currentSlotIdx >= 0;
        boolean isInBlacklist = blacklistService.isInBlacklist(mac);
        int blacklistedFlag = rule.getBlacklistedBySupervision() != null ? rule.getBlacklistedBySupervision() : 0;

        // Load cumulative counters from DB (in seconds)
        int usedTodaySeconds = rule.getUsedToday() != null ? rule.getUsedToday() : 0;
        int sessionSeconds = rule.getSessionUsed() != null ? rule.getSessionUsed() : 0;
        // Decode: sessionSeconds < 0 means blacklisted in slot (-sessionSeconds - 1)
        int blacklistedSlotIdx = sessionSeconds < 0 ? (-sessionSeconds - 1) : -1;

        String hostname = rule.getHostname() != null ? rule.getHostname() : mac;
        String result = "ok";

        if (isOnline) {
            log.info("[{}] 在线 路由器上报{}秒 | 在时间段内={}(slot{}) | 在黑名单={} | 监管拉黑={} | 会话已用{}秒 累计已用{}秒 | 单次限制={}分 每日限制={}分",
                    hostname, routerOnlineSeconds, inTimeSlot, currentSlotIdx, isInBlacklist, blacklistedFlag,
                    sessionSeconds, usedTodaySeconds, rule.getSingleDuration(), rule.getDailyLimit());

            if (!inTimeSlot) {
                log.warn("[{}] 不在允许时间段内, 应当断网", hostname);
                if (!isInBlacklist) {
                    log.warn(">>> 执行拉黑: {} (不在允许时间段)", mac);
                    blacklistService.add(mac, hostname);
                    blacklistedFlag = 1;
                    logSupervisionEvent(rule, device, "blacklist", "不在允许时间段, 自动拉黑");
                    result = "blacklisted";
                } else {
                    log.info("[{}] 已在黑名单中, 跳过", hostname);
                }
            } else {
                // Within allowed time slot
                if (blacklistedFlag == 1 && isInBlacklist) {
                    // Unblock if entering any time slot from outside, or switching to a different slot
                    boolean slotChanged = blacklistedSlotIdx < 0 || blacklistedSlotIdx != currentSlotIdx;
                    if (slotChanged) {
                        log.warn(">>> 执行解除黑名单: {} (进入新时间段 slot{}→slot{}, 单次时长重置)",
                                mac, blacklistedSlotIdx, currentSlotIdx);
                        blacklistService.delete(Collections.singletonList(mac));
                        blacklistedFlag = 0;
                        sessionSeconds = TICK_SECONDS; // fresh session
                        usedTodaySeconds += TICK_SECONDS;
                        logSupervisionEvent(rule, device, "normal",
                                "进入新时间段, 自动解除黑名单, 单次时长重置");
                        result = "unblocked";
                    } else {
                        log.info("[{}] 在当前时间段内已被拉黑(slot{}), 保持黑名单, 不累积时长", hostname, currentSlotIdx);
                        result = "blacklisted";
                    }
                } else {
                    // Not blacklisted — accumulate normally
                    if (sessionSeconds < 0) sessionSeconds = 0;
                    sessionSeconds += TICK_SECONDS;
                    usedTodaySeconds += TICK_SECONDS;
                    result = "accumulated";
                    log.info("[{}] 在允许时间段内, 累积 +{}秒 → 会话{}秒 ({}分钟) 总计{}秒 ({}分钟)",
                            hostname, TICK_SECONDS, sessionSeconds, sessionSeconds / 60,
                            usedTodaySeconds, usedTodaySeconds / 60);

                    // Single duration check
                    int singleLimitMin = rule.getSingleDuration() != null ? rule.getSingleDuration() : 0;
                    int sessionUsedMin = sessionSeconds / 60;
                    if (singleLimitMin > 0 && sessionUsedMin >= singleLimitMin) {
                        log.warn("[{}] 单次使用超时: 会话{}分钟 >= 限制{}分钟", hostname, sessionUsedMin, singleLimitMin);
                        if (!isInBlacklist) {
                            log.warn(">>> 执行拉黑: {} (单次超时)", mac);
                            blacklistService.add(mac, hostname);
                            blacklistedFlag = 1;
                            sessionSeconds = -(currentSlotIdx + 1); // encode slot for later unblock
                            logSupervisionEvent(rule, device, "timeout",
                                    "单次使用超时: " + sessionUsedMin + "/" + singleLimitMin + "分钟");
                            result = "blacklisted";
                        }
                    }

                    // Daily total check (compare cumulative daily minutes against limit)
                    int totalLimitMin = rule.getDailyLimit() != null ? rule.getDailyLimit() : 0;
                    int dailyUsedMin = usedTodaySeconds / 60;
                    if (totalLimitMin > 0 && dailyUsedMin >= totalLimitMin) {
                        log.warn("[{}] 每日时长用尽: 累计{}分钟 >= 总额{}分钟", hostname, dailyUsedMin, totalLimitMin);
                        if (!isInBlacklist) {
                            log.warn(">>> 执行拉黑: {} (每日时长用尽)", mac);
                            blacklistService.add(mac, hostname);
                            blacklistedFlag = 1;
                            sessionSeconds = -(currentSlotIdx + 1);
                            logSupervisionEvent(rule, device, "timeout",
                                    "每日时长用尽: " + dailyUsedMin + "/" + totalLimitMin + "分钟");
                            result = "blacklisted";
                        }
                    }
                }
            }
        } else {
            log.info("[{}] 离线 | 在时间段内={}(slot{}) | 在黑名单={} | 监管拉黑={} | 会话已用{}秒 累计已用{}秒",
                    hostname, inTimeSlot, currentSlotIdx, isInBlacklist, blacklistedFlag, sessionSeconds, usedTodaySeconds);

            if (inTimeSlot && blacklistedFlag == 1 && isInBlacklist) {
                boolean isNewSlot = blacklistedSlotIdx < 0 || blacklistedSlotIdx != currentSlotIdx;
                if (isNewSlot) {
                    // Entered a different time slot — reset single-use, unblock
                    int dailyUsedMin = usedTodaySeconds / 60;
                    log.warn(">>> 执行解除黑名单(离线): {} (进入新时间段slot{}→slot{}, 单次时长重置, 已用{}分)",
                            mac, blacklistedSlotIdx, currentSlotIdx, dailyUsedMin);
                    blacklistService.delete(Collections.singletonList(mac));
                    blacklistedFlag = 0;
                    sessionSeconds = 0;
                    result = "unblocked";
                } else {
                    log.info("[{}] 在当前时间段内(slot{})已被拉黑, 保持黑名单", hostname, currentSlotIdx);
                }
            }
        }

        // Persist accumulated seconds (daily + session) and blacklist flag
        supervisionService.updateUsage(rule.getId(), usedTodaySeconds, sessionSeconds, blacklistedFlag);
        return result;
    }

    private int getCurrentSlotIndex(String timeSlotsJson) {
        if (timeSlotsJson == null || timeSlotsJson.isEmpty()) return -1;
        try {
            List<String> slots = objectMapper.readValue(timeSlotsJson, new TypeReference<List<String>>() {});
            LocalTime now = LocalTime.now();
            for (int i = 0; i < slots.size(); i++) {
                String[] parts = slots.get(i).split("-");
                if (parts.length == 2) {
                    LocalTime start = LocalTime.parse(parts[0].trim(), DateTimeFormatter.ofPattern("HH:mm"));
                    LocalTime end = LocalTime.parse(parts[1].trim(), DateTimeFormatter.ofPattern("HH:mm"));
                    if (!now.isBefore(start) && !now.isAfter(end)) {
                        return i;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析时间段失败: {}", timeSlotsJson, e);
        }
        return -1;
    }

    /** 通过 MAC 判断是否为 PC 监管规则 */
    private boolean isPcRule(String mac) {
        if (mac == null || mac.isEmpty()) return false;
        return pcDeviceService.findByMac(mac) != null;
    }

    /**
     * 处理 PC 监管规则 — 逻辑与 processRule 镜像，但执行动作为 Agent 指令而非路由器黑名单
     */
    private String processPcRule(SupervisionRule rule) {
        String mac = rule.getMac();
        PcDevice pcDevice = pcDeviceService.findByMac(mac);
        if (pcDevice == null) return "skipped";

        // 判断 PC 在线：lastHeartbeat 30秒内
        boolean isOnline = pcDevice.getLastHeartbeat() != null
                && pcDevice.getLastHeartbeat().plusSeconds(30).isAfter(LocalDateTime.now());

        // 延时模式：跳过所有监管检查，仅累积时长
        int extendActive = rule.getExtendActive() != null ? rule.getExtendActive() : 0;
        if (extendActive == 1) {
            int usedTodaySeconds = rule.getUsedToday() != null ? rule.getUsedToday() : 0;
            if (isOnline) {
                usedTodaySeconds += TICK_SECONDS;
                log.info("[PC:{}] 延时模式中(到期{}), 跳过监管巡视, 仅累积时长 → 今日已用{}秒",
                        rule.getHostname(), rule.getExtendExpireAt(), usedTodaySeconds);
            } else {
                log.info("[PC:{}] 延时模式中(到期{}), PC离线, 跳过监管巡视",
                        rule.getHostname(), rule.getExtendExpireAt());
            }
            supervisionService.updateUsage(rule.getId(), usedTodaySeconds, 0, 0);
            return "extended";
        }

        int currentSlotIdx = getCurrentSlotIndex(rule.getTimeSlots());
        boolean inTimeSlot = currentSlotIdx >= 0;
        int blacklistedFlag = rule.getBlacklistedBySupervision() != null ? rule.getBlacklistedBySupervision() : 0;

        int usedTodaySeconds = rule.getUsedToday() != null ? rule.getUsedToday() : 0;
        int sessionSeconds = rule.getSessionUsed() != null ? rule.getSessionUsed() : 0;
        int blacklistedSlotIdx = sessionSeconds < 0 ? (-sessionSeconds - 1) : -1;

        String hostname = rule.getHostname() != null ? rule.getHostname() : mac;
        String result = "ok";

        if (isOnline) {
            log.info("[PC:{}] 在线 | 在时间段内={}(slot{}) | 监管锁屏={} | 会话已用{}秒 累计已用{}秒 | 单次限制={}分 每日限制={}分",
                    hostname, inTimeSlot, currentSlotIdx, blacklistedFlag,
                    sessionSeconds, usedTodaySeconds, rule.getSingleDuration(), rule.getDailyLimit());

            if (!inTimeSlot) {
                log.warn("[PC:{}] 不在允许时间段内, 执行锁屏", hostname);
                if (blacklistedFlag == 0) {
                    pcCommandService.create(pcDevice.getId(), "SHOW_MESSAGE",
                            "{\"message\":\"不在允许使用时间段，屏幕已锁定\"}");
                    pcCommandService.create(pcDevice.getId(), "LOCK_SCREEN", null);
                    blacklistedFlag = 1;
                    log.info("[PC:{}] >>> 发送锁屏指令(不在时间段)", mac);
                    result = "locked";
                }
            } else {
                // 在允许时间段内
                if (blacklistedFlag == 1) {
                    // 检查是否切换了时间段
                    boolean slotChanged = blacklistedSlotIdx < 0 || blacklistedSlotIdx != currentSlotIdx;
                    if (slotChanged) {
                        log.warn("[PC:{}] >>> 进入新时间段(slot{}→slot{}), 解除限制, 单次时长重置",
                                mac, blacklistedSlotIdx, currentSlotIdx);
                        pcCommandService.create(pcDevice.getId(), "SHOW_MESSAGE",
                                "{\"message\":\"监管时段已刷新，您可以使用电脑了\"}");
                        blacklistedFlag = 0;
                        sessionSeconds = TICK_SECONDS;
                        usedTodaySeconds += TICK_SECONDS;
                        warnedPcRules.remove(rule.getId());
                        result = "unlocked";
                    } else {
                        log.info("[PC:{}] 在当前时间段内(slot{})已被锁屏, 保持限制", hostname, currentSlotIdx);
                        result = "locked";
                    }
                } else {
                    // 正常累积时长
                    if (sessionSeconds < 0) sessionSeconds = 0;
                    sessionSeconds += TICK_SECONDS;
                    usedTodaySeconds += TICK_SECONDS;
                    result = "accumulated";
                    log.info("[PC:{}] 在允许时间段内, 累积+{}秒 → 会话{}秒({}分钟) 总计{}秒({}分钟)",
                            hostname, TICK_SECONDS, sessionSeconds, sessionSeconds / 60,
                            usedTodaySeconds, usedTodaySeconds / 60);

                    // 单次时长 5 分钟预警（含延长按钮）
                    int singleLimitMin = rule.getSingleDuration() != null ? rule.getSingleDuration() : 0;
                    int sessionUsedMin = sessionSeconds / 60;
                    if (singleLimitMin > 5 && blacklistedFlag == 0) {
                        int remainingMin = singleLimitMin - sessionUsedMin;
                        if (remainingMin > 0 && remainingMin <= 5 && warnedPcRules.add(rule.getId())) {
                            String extendParams = String.format(
                                "{\"message\":\"单次使用时间还剩 %d 分钟\",\"extendPrompt\":true,\"ruleId\":%d,\"extendMinutes\":10}",
                                remainingMin, rule.getId()
                            );
                            pcCommandService.create(pcDevice.getId(), "SHOW_MESSAGE", extendParams);
                            log.info("[PC:{}] >>> 发送使用预警: 还剩{}分钟(已用{}/{}分钟), 含延长按钮",
                                    hostname, remainingMin, sessionUsedMin, singleLimitMin);
                        }
                    }

                    // 单次时长检查
                    if (singleLimitMin > 0 && sessionUsedMin >= singleLimitMin) {
                        log.warn("[PC:{}] 单次使用超时: 会话{}分钟 >= 限制{}分钟", hostname, sessionUsedMin, singleLimitMin);
                        if (blacklistedFlag == 0) {
                            pcCommandService.create(pcDevice.getId(), "SHOW_MESSAGE",
                                    "{\"message\":\"单次使用时间已到(" + sessionUsedMin + "/" + singleLimitMin + "分钟)，屏幕已锁定\"}");
                            pcCommandService.create(pcDevice.getId(), "LOCK_SCREEN", null);
                            blacklistedFlag = 1;
                            sessionSeconds = -(currentSlotIdx + 1);
                            warnedPcRules.remove(rule.getId());
                            log.info("[PC:{}] >>> 发送锁屏指令(单次超时)", mac);
                            result = "locked";
                        }
                    }

                    // 每日总时长检查
                    int totalLimitMin = rule.getDailyLimit() != null ? rule.getDailyLimit() : 0;
                    int dailyUsedMin = usedTodaySeconds / 60;
                    if (totalLimitMin > 0 && dailyUsedMin >= totalLimitMin && blacklistedFlag == 0) {
                        log.warn("[PC:{}] 每日时长用尽: 累计{}分钟 >= 总额{}分钟", hostname, dailyUsedMin, totalLimitMin);
                        pcCommandService.create(pcDevice.getId(), "SHOW_MESSAGE",
                                "{\"message\":\"每日使用时长已用尽(" + dailyUsedMin + "/" + totalLimitMin + "分钟)，屏幕已锁定\"}");
                        pcCommandService.create(pcDevice.getId(), "LOCK_SCREEN", null);
                        blacklistedFlag = 1;
                        sessionSeconds = -(currentSlotIdx + 1);
                        warnedPcRules.remove(rule.getId());
                        log.info("[PC:{}] >>> 发送锁屏指令(每日用尽)", mac);
                        result = "locked";
                    }
                }
            }
        } else {
            log.info("[PC:{}] 离线 | 在时间段内={}(slot{}) | 监管锁屏={} | 会话已用{}秒 累计已用{}秒",
                    hostname, inTimeSlot, currentSlotIdx, blacklistedFlag, sessionSeconds, usedTodaySeconds);

            // PC 离线但时间段变化时的处理
            if (inTimeSlot && blacklistedFlag == 1) {
                boolean isNewSlot = blacklistedSlotIdx < 0 || blacklistedSlotIdx != currentSlotIdx;
                if (isNewSlot) {
                    log.warn("[PC:{}] >>> 进入新时间段(离线slot{}→slot{}), 解除限制",
                            mac, blacklistedSlotIdx, currentSlotIdx);
                    blacklistedFlag = 0;
                    sessionSeconds = 0;
                    warnedPcRules.remove(rule.getId());
                    result = "unlocked";
                }
            }
        }

        supervisionService.updateUsage(rule.getId(), usedTodaySeconds, sessionSeconds, blacklistedFlag);
        return result;
    }

    private void logSupervisionEvent(SupervisionRule rule, TerminalDevice device, String logType, String remark) {
        ConnectionLog l = new ConnectionLog();
        l.setDeviceName(rule.getHostname() != null ? rule.getHostname() : device.getMac());
        l.setMac(rule.getMac());
        l.setConnectTime(LocalDateTime.now().minusMinutes(device.getOnlinetime() / 60));
        l.setDisconnectTime(LocalDateTime.now());
        l.setDuration(device.getOnlinetime() / 60);
        l.setConnectCount(1);
        l.setLogType(logType);
        l.setStatus("pending");
        l.setSource("supervision");
        l.setRemark(remark);
        logService.save(l);
        log.info("[日志] {} — {}", rule.getHostname(), remark);
    }

    private void checkWhitelistViolation(TerminalDevice device) {
        int onlineMinutes = device.getOnlinetime() / 60;
        if (onlineMinutes > 30) {
            log.warn("[白名单外] {} 长时间在线: {}分钟", device.getHostName(), onlineMinutes);
            ConnectionLog l = new ConnectionLog();
            l.setDeviceName(device.getHostName() != null ? device.getHostName() : device.getMac());
            l.setMac(device.getMac());
            l.setConnectTime(LocalDateTime.now().minusMinutes(onlineMinutes));
            l.setDisconnectTime(LocalDateTime.now());
            l.setDuration(onlineMinutes);
            l.setConnectCount(1);
            l.setLogType("whitelist_violation");
            l.setStatus("pending");
            l.setSource("whitelist");
            l.setRemark("白名单外设备长时间在线: " + onlineMinutes + "分钟");
            logService.save(l);
        }
    }
}
