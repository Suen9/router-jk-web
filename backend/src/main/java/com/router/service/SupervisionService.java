package com.router.service;

import com.router.model.dto.SupervisionVO;
import com.router.model.entity.SupervisionRule;

import java.util.List;

public interface SupervisionService {
    List<SupervisionVO> list();
    SupervisionRule add(SupervisionRule rule);
    SupervisionRule updateRule(Long id, SupervisionRule rule);
    boolean extendTime(Long id, int extraMinutes);
    boolean remove(Long id);
    List<SupervisionRule> getActiveRules();
    void updateUsage(Long id, int usedToday, int sessionUsed, int blacklisted);
    void revertExtensions();
    /** Check all active rules for expired extension timers and blacklist them. */
    int checkExtendExpiry();
}
