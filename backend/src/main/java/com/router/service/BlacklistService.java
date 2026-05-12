package com.router.service;

import com.router.model.dto.BlacklistItem;

import java.util.List;

public interface BlacklistService {
    List<BlacklistItem> getBlacklist();
    boolean add(String mac, String name);
    boolean delete(List<String> macList);
    boolean isInBlacklist(String mac);
}
