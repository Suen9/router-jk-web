package com.router.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.router.mapper.SupervisionRuleMapper;
import com.router.model.dto.SupervisionVO;
import com.router.model.entity.SupervisionRule;
import com.router.service.BlacklistService;
import com.router.service.SupervisionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class SupervisionServiceImpl implements SupervisionService {

    private static final Logger log = LoggerFactory.getLogger(SupervisionServiceImpl.class);

    private final SupervisionRuleMapper mapper;
    private final BlacklistService blacklistService;

    public SupervisionServiceImpl(SupervisionRuleMapper mapper, BlacklistService blacklistService) {
        this.mapper = mapper;
        this.blacklistService = blacklistService;
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
            vo.setStatus(r.getStatus());
            vo.setRemark(r.getRemark());
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

        // Unblock device from router blacklist
        boolean unblocked = false;
        try {
            blacklistService.delete(java.util.Collections.singletonList(mac));
            log.info("[延时] 已解除路由黑名单 mac={}", mac);
            unblocked = true;
        } catch (Exception e) {
            log.error("[延时] 解除路由黑名单失败 mac={}: {}", mac, e.getMessage());
        }
        rule.setBlacklistedBySupervision(0);
        rule.setSessionUsed(0);

        mapper.updateById(rule);

        log.info("[延时] 完成: {} 延长{}分钟 | extend_active=1 expire={} | 时间段={} | 解拉黑={} | 单次/每日不变",
                hostname, extraMinutes, rule.getExtendExpireAt(), newTimeSlots, unblocked);
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
                log.info("[延时到期] {} mac={} | extend_expire_at={} 已到期, 执行拉黑",
                        rule.getHostname(), rule.getMac(), rule.getExtendExpireAt());
                try {
                    if (rule.getMac() != null) {
                        blacklistService.add(rule.getMac(), rule.getHostname() != null ? rule.getHostname() : rule.getMac());
                    }
                } catch (Exception e) {
                    log.error("[延时到期] 拉黑失败 mac={}: {}", rule.getMac(), e.getMessage());
                }
                rule.setExtendActive(0);
                rule.setExtendExpireAt(null);
                rule.setBlacklistedBySupervision(1);
                // Encode current slot index so scheduler won't unblock in same slot
                int currentSlot = getCurrentSlotIndex(rule.getTimeSlots());
                rule.setSessionUsed(currentSlot >= 0 ? -(currentSlot + 1) : 0);
                log.info("[延时到期] 拉黑完成, session_used={} (slot{})", rule.getSessionUsed(), currentSlot);
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
                if (rule.getMac() != null) {
                    try {
                        blacklistService.delete(java.util.Collections.singletonList(rule.getMac()));
                    } catch (Exception e) { /* ignore */ }
                }
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
        if (rule.getMac() != null && rule.getBlacklistedBySupervision() != null
                && rule.getBlacklistedBySupervision() == 1
                && blacklistService.isInBlacklist(rule.getMac())) {
            blacklistService.delete(java.util.Collections.singletonList(rule.getMac()));
        }
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
}
