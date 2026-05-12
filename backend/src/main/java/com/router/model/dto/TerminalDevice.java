package com.router.model.dto;

import lombok.Data;

@Data
public class TerminalDevice {
    private String userIp;
    private String deviceAliasName;
    private int active;
    private int rssi;
    private String hostName;
    private String flowDown;
    private String manufacture;
    private String activeTime;
    private int onlinetime;
    private String flowUp;
    private String userIpV6;
    private String ssid;
    private int idx;
    private int up;
    private String sn;
    private String groupId;
    private int internetaccess;
    private String groupName;
    private String osType;
    private int bannerType;
    private String wifiUpDown;
    private String mac;
    private String band;
    private String channel;
    private String rxrate;
    private String hardwareType;
    private int down;
    private int connectType;
    private String uploadspeed;
    private String downloadspeed;
    private int usbandwidth;
    private int dsbandwidth;
}
