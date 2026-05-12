package com.router.service;

import com.router.model.entity.WhitelistDevice;
import java.util.List;

public interface WhitelistService {
    List<WhitelistDevice> list();
    WhitelistDevice add(WhitelistDevice device);
    boolean remove(Long id);
    boolean isInWhitelist(String mac);
}
