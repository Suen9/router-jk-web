package com.router.service;

import com.router.model.dto.DeviceDetail;
import com.router.model.dto.TerminalDevice;

import java.util.List;
import java.util.Map;

public interface TerminalService {
    List<TerminalDevice> getTerminalList(int page, int size, String keyword, String accessType, String status);
    DeviceDetail getDetail(int idx);
    boolean setAccess(int idx, int internetaccess);
    boolean setSpeedLimit(int idx, int usbandwidth, int dsbandwidth, String uploadspeed, String downloadspeed);
    boolean addToBlacklist(String mac, String name);
    boolean addToSupervision(String mac, String hostname);
}
