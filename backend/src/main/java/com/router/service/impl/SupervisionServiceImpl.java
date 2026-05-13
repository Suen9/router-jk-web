package com.router.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.router.mapper.PcDeviceMapper;
import com.router.mapper.SupervisionRuleMapper;
import com.router.model.dto.SupervisionVO;
import com.router.model.entity.PcDevice;
import com.router.model.entity.SupervisionRule;
import com.router.service.BlacklistService;
import com.router.service.PcCommandService;
import com.router.service.SupervisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class SupervisionServiceImpl implements SupervisionService {

    private static final Logger log = LoggerFactory.getLogger(SupervisionServiceImpl.class);

    private final SupervisionRuleMapper mapper;
    private final BlacklistService blacklistService;
    private final PcDeviceMapper pcDeviceMapper;
    private final PcCommandService pcCommandService;

    public SupervisionServiceImpl(SupervisionRuleMapper mapper, BlacklistService blacklistService,
                                   PcDeviceMapper pcDeviceMapper, PcCommandService pcCommandService) {
        this.mapper = mapper;
        this.blacklistService = blacklistService;
        this.pcDeviceMapper = pcDeviceMapper;
        this.pcCommandService = pcCommandService;
    }

    @Override
    public List<SupervisionVO> list() {
        List<SupervisionRule> rules = mapper.selectList(null);
        List<SupervisionVO> vos = new ArrayList<>();
        for (SupervisionRule r : rules) {
            SupervisionVO vo = new SupervisionVO();
            vo.setId(r.getId());
            vo.setHostname(r.getHostname());
            vo.setMac(r.getMac());
            vo.setTimeSlots(r.getTimeSlots());
            vo.setSingleDuration(r.getSingleDuration());
            vo.setDailyLimit(r.getDailyLimit());
            int usedSec = r.getUsedToday() != null ? r.getUsedToday() : 0;
            int usedMin = usedSec / 60;
            vo.setUsedToday(usedMin);
            int dailyMin = r.getDailyLimit() != null ? r.getDailyLimit() : 60;
            vo.setRemaining(Math.max(dailyMin - usedMin, 0));
            vo.setBlacklistedBySupervision(r.getBlacklistedBySupervision());
            vo.setExtendActive(r.getExtendActive());
            // 延时剩余分钟数
            if (r.getExtendActive() != null && r.getExtendActive() == 1 && r.getExtendExpireAt() != null) {
                long remainSec = java.time.Duration.between(LocalDateTime.now(), r.getExtendExpireAt()).getSeconds();
                vo.setExtendExpireMinutes((int) Math.max(remainSec / 60, 0));
            }
            vo.setStatus(r.getStatus());
            vo.setRemark(r.getRemark());

            // MAC 匹配 PC 设备时填充 PC 相关信息
            PcDevice pcDevice = getPcDeviceByMac(r.getMac());
            if (pcDevice != null) {
                vo.setPcHostname(pcDevice.getHostname());
                boolean online = pcDevice.getLastHeartbeat() != null
                        && pcDevice.getLastHeartbeat().plusSeconds(30).isAfter(LocalDateTime.now());
                vo.setPcOnline(online ? 1 : 0);
                vo.setPcUsedTodayMinutes(usedMin);
            }
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public SupervisionRule add(SupervisionRule rule) {
        rule.setUsedToday(0);
        rule.setSessionUsed(0);
        rule.setExtendActive(0);
        rule.setBlacklistedBySupervision(0);
        rule.setStatus("active");
        mapper.insert(rule);
        return rule;
    }

    @Override
    public SupervisionRule updateRule(Long id, SupervisionRule rule) {
        rule.setId(id);
        mapper.updateById(rule);
        return rule;
    }

    @Override
    public boolean extendTime(Long id, int extraMinutes) {
        SupervisionRule rule = mapper.selectById(id);
        if (rule == null) {
            log.warn("[延时] 规则不存在 id={}", id);
            return false;
        }

        String mac = rule.getMac();
        String hostname = rule.getHostname();
        String oldTimeSlots = rule.getTimeSlots();
        int usedToday = rule.getUsedToday() != null ? rule.getUsedToday() : 0;
        int blacklistedFlag = rule.getBlacklistedBySupervision() != null ? rule.getBlacklistedBySupervision() : 0;

        log.info("[延时] {} mac={} | 延长{}分钟 | 拉黑标记={} | 已用今日{}秒 | 延前时间段={}",
                hostname, mac, extraMinutes, blacklistedFlag, usedToday, oldTimeSlots);

        // Snapshot original time slots for midnight revert (first extension today only)
        if (rule.getOriginalTimeSlots() == null || rule.getOriginalTimeSlots().isEmpty()) {
            rule.setOriginalTimeSlots(oldTimeSlots);
            log.info("[延时] 首次延时, 快照 original_time_slots = {}", oldTimeSlots);
        }

        // Extend current time slot end time only — do NOT modify singleDuration/dailyLimit
        // The timer bypasses all checks, so global limits stay unchanged for other slots
        String newTimeSlots = extendTimeSlotEnd(oldTimeSlots, extraMinutes);
        rule.setTimeSlots(newTimeSlots);

        // Activate extension mode: skip supervision, set timer
        rule.setExtendActive(1);
        rule.setExtendExpireAt(java.time.LocalDateTime.now().plusMinutes(extraMinutes));

        // 解除限制：PC 规则跳过路由器操作，改为通知 Agent
        PcDevice pcDevice = getPcDeviceByMac(mac);
        boolean unblocked = false;
        if (pcDevice != null) {
            // PC 规则：发送 SHOW_MESSAGE 通知 PC 时间已延长
            try {
                String msg = String.format("管理员已为您延长 %d 分钟使用时间，请及时保存工作", extraMinutes);
                pcCommandService.create(pcDevice.getId(), "SHOW_MESSAGE",
                        "{\"message\":\"" + msg + "\"}");
                log.info("[延时] PC端已发送延长通知 pcDeviceId={} hostname={}", pcDevice.getId(), pcDevice.getHostname());
            } catch (Exception e) {
                log.error("[延时] 发送PC通知失败 mac={}: {}", mac, e.getMessage());
            }
        } else {
            // 终端规则：解除路由器黑名单
            try {
                blacklistService.delete(Collections.singletonList(mac));
                log.info("[延时] 已解除路由黑名单 mac={}", mac);
                unblocked = true;
            } catch (Exception e) {
                log.error("[延时] 解除路由黑名单失败 mac={}: {}", mac, e.getMessage());
            }
        }
        rule.setBlacklistedBySupervision(0);
        rule.setSessionUsed(0);

        mapper.updateById(rule);

        log.info("[延时] 完成: {} 延长{}分钟 | extend_active=1 expire={} | 时间段={} | isPc={} | 解拉黑={} | 单次/每日不变",
                hostname, extraMinutes, rule.getExtendExpireAt(), newTimeSlots, pcDevice != null, unblocked);
        return true;
    }

    /**
     * Check all active rules for expired extension timers.
     * If extend_active==1 and now >= extend_expire_at: blacklist device, clear extend mode.
     * Called by the scheduler each tick before normal processing.
     * @return number of devices blacklisted due to extension expiry
     */
    @Override
    public int checkExtendExpiry() {
        List<SupervisionRule> rules = getActiveRules();
        int count = 0;
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (SupervisionRule rule : rules) {
            int active = rule.getExtendActive() != null ? rule.getExtendActive() : 0;
            if (active == 1 && rule.getExtendExpireAt() != null && !now.isBefore(rule.getExtendExpireAt())) {
                String mac = rule.getMac();
                PcDevice pcDevice = getPcDeviceByMac(mac);
                log.info("[延时到期] {} mac={} | extend_expire_at={} 已到期, isPc={}",
                        rule.getHostname(), mac, rule.getExtendExpireAt(), pcDevice != null);
                if (pcDevice != null) {
                    // PC 规则：发送 LOCK_SCREEN + SHOW_MESSAGE
                    try {
                        pcCommandService.create(pcDevice.getId(), "SHOW_MESSAGE",
                                "{\"message\":\"延时时间已到，屏幕已锁定\"}");
                        pcCommandService.create(pcDevice.getId(), "LOCK_SCREEN", null);
                        log.info("[延时到期] PC端已发送锁屏指令 pcDeviceId={}", pcDevice.getId());
                    } catch (Exception e) {
                        log.error("[延时到期] 发送PC指令失败 mac={}: {}", mac, e.getMessage());
                    }
                } else {
                    // 终端规则：加入路由器黑名单
                    try {
                        if (mac != null) {
                            blacklistService.add(mac, rule.getHostname() != null ? rule.getHostname() : mac);
                        }
                    } catch (Exception e) {
                        log.error("[延时到期] 拉黑失败 mac={}: {}", mac, e.getMessage());
                    }
                }
                rule.setExtendActive(0);
                rule.setExtendExpireAt(null);
                rule.setBlacklistedBySupervision(1);
                // Encode current slot index so scheduler won't unblock in same slot
                int currentSlot = getCurrentSlotIndex(rule.getTimeSlots());
                rule.setSessionUsed(currentSlot >= 0 ? -(currentSlot + 1) : 0);
                log.info("[延时到期] 完成, session_used={} (slot{})", rule.getSessionUsed(), currentSlot);
                mapper.updateById(rule);
                count++;
            }
        }
        if (count > 0) {
            log.info("[延时到期] 共处理 {} 台设备", count);
        }
        return count;
    }

    @Override
    public void revertExtensions() {
        List<SupervisionRule> rules = getActiveRules();
        for (SupervisionRule rule : rules) {
            boolean changed = false;
            // Restore original time slots if extension modified them
            if (rule.getOriginalTimeSlots() != null && !rule.getOriginalTimeSlots().isEmpty()) {
                rule.setTimeSlots(rule.getOriginalTimeSlots());
                rule.setOriginalTimeSlots(null);
                changed = true;
            }
            // Clear extension mode
            if ((rule.getExtendActive() != null && rule.getExtendActive() == 1)
                    || rule.getExtendExpireAt() != null) {
                rule.setExtendActive(0);
                rule.setExtendExpireAt(null);
                changed = true;
            }
            // Unblock if blacklisted (new day, fresh start)
            if (rule.getBlacklistedBySupervision() != null && rule.getBlacklistedBySupervision() == 1) {
                PcDevice pcDevice = getPcDeviceByMac(rule.getMac());
                if (pcDevice == null && rule.getMac() != null) {
                    // 终端规则：从路由器黑名单解除
                    try {
                        blacklistService.delete(Collections.singletonList(rule.getMac()));
                    } catch (Exception e) { /* ignore */ }
                }
                // PC 规则：跳过路由器操作，午夜自动恢复即可
                rule.setBlacklistedBySupervision(0);
                changed = true;
            }
            if (changed) {
                mapper.updateById(rule);
            }
        }
    }

    /** Extend the end time of the currently active or most recently ended time slot. */
    private String extendTimeSlotEnd(String timeSlotsJson, int extraMinutes) {
        if (timeSlotsJson == null || timeSlotsJson.isEmpty()) return timeSlotsJson;
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            List<String> slots = om.readValue(timeSlotsJson, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            if (slots.isEmpty()) return timeSlotsJson;

            java.time.LocalTime now = java.time.LocalTime.now();
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm");

            int targetIdx = -1;
            long minGap = Long.MAX_VALUE;
            for (int i = 0; i < slots.size(); i++) {
                String[] parts = slots.get(i).split("-");
                if (parts.length == 2) {
                    java.time.LocalTime start = java.time.LocalTime.parse(parts[0].trim(), fmt);
                    java.time.LocalTime end = java.time.LocalTime.parse(parts[1].trim(), fmt);
                    if (!now.isBefore(start) && !now.isAfter(end)) { targetIdx = i; break; }
                    if (now.isAfter(end)) {
                        long gap = now.toSecondOfDay() - end.toSecondOfDay();
                        if (gap < minGap) { minGap = gap; targetIdx = i; }
                    }
                }
            }

            if (targetIdx >= 0) {
                String[] parts = slots.get(targetIdx).split("-");
                java.time.LocalTime end = java.time.LocalTime.parse(parts[1].trim(), fmt);
                java.time.LocalTime newEnd = end.plusMinutes(extraMinutes);
                slots.set(targetIdx, parts[0].trim() + "-" + newEnd.format(fmt));
            }

            return om.writeValueAsString(slots);
        } catch (Exception e) {
            return timeSlotsJson;
        }
    }

    /** Find the currently active time slot index, or -1 if not in any slot. */
    private int getCurrentSlotIndex(String timeSlotsJson) {
        if (timeSlotsJson == null || timeSlotsJson.isEmpty()) return -1;
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            List<String> slots = om.readValue(timeSlotsJson, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            java.time.LocalTime now = java.time.LocalTime.now();
            java.time.format.DateTimeFormatter fmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
            for (int i = 0; i < slots.size(); i++) {
                String[] parts = slots.get(i).split("-");
                if (parts.length == 2) {
                    java.time.LocalTime start = java.time.LocalTime.parse(parts[0].trim(), fmt);
                    java.time.LocalTime end = java.time.LocalTime.parse(parts[1].trim(), fmt);
                    if (!now.isBefore(start) && !now.isAfter(end)) return i;
                }
            }
        } catch (Exception e) { /* ignore */ }
        return -1;
    }

    @Override
    public boolean remove(Long id) {
        SupervisionRule rule = mapper.selectById(id);
        if (rule == null) return false;
        // 终端规则且被监管拉黑：从路由器黑名单解除
        PcDevice pcDevice = getPcDeviceByMac(rule.getMac());
        if (pcDevice == null && rule.getMac() != null && rule.getBlacklistedBySupervision() != null
                && rule.getBlacklistedBySupervision() == 1
                && blacklistService.isInBlacklist(rule.getMac())) {
            blacklistService.delete(Collections.singletonList(rule.getMac()));
        }
        // PC 规则：无需操作路由器，直接删除规则即可
        return mapper.deleteById(id) > 0;
    }

    @Override
    public void updateUsage(Long id, int usedToday, int sessionUsed, int blacklisted) {
        SupervisionRule rule = mapper.selectById(id);
        if (rule != null) {
            rule.setUsedToday(usedToday);
            rule.setSessionUsed(sessionUsed);
            rule.setBlacklistedBySupervision(blacklisted);
            mapper.updateById(rule);
        }
    }

    @Override
    public List<SupervisionRule> getActiveRules() {
        QueryWrapper<SupervisionRule> qw = new QueryWrapper<>();
        qw.eq("status", "active");
        return mapper.selectList(qw);
    }

    /** 通过 MAC 查询匹配的 PC 设备 */
    private PcDevice getPcDeviceByMac(String mac) {
        if (mac == null || mac.isEmpty()) return null;
        QueryWrapper<PcDevice> qw = new QueryWrapper<>();
        qw.eq("mac", mac);
        return pcDeviceMapper.selectOne(qw);
    }

    /** 判断规则是否应用于 PC 设备 */
    boolean isPcRule(SupervisionRule rule) {
        return rule.getMac() != null && getPcDeviceByMac(rule.getMac()) != null;
    }
}
