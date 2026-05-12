package com.router.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.router.model.dto.DeviceDetail;
import com.router.model.dto.TerminalDevice;
import com.router.service.RouterApiService;
import com.router.service.TerminalService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class TerminalServiceImpl implements TerminalService {

    private final RouterApiService routerApi;

    public TerminalServiceImpl(RouterApiService routerApi) {
        this.routerApi = routerApi;
    }

    @Override
    public List<TerminalDevice> getTerminalList(int page, int size, String keyword, String accessType, String status) {
        JsonNode res = routerApi.getForJson("/api/v1/lua/OnlineUsers/sta_list?page=" + page + "&size=" + size);
        List<TerminalDevice> devices = new ArrayList<>();
        if (res != null && res.has("data") && res.get("data").has("list")) {
            for (JsonNode item : res.get("data").get("list")) {
                TerminalDevice d = new TerminalDevice();
                d.setIdx(item.has("idx") ? item.get("idx").asInt() : 0);
                d.setMac(item.has("mac") ? item.get("mac").asText() : "");
                d.setUserIp(item.has("userIp") ? item.get("userIp").asText() : "");
                d.setHostName(item.has("hostName") && !item.get("hostName").asText().isEmpty() ? item.get("hostName").asText() : "-");
                d.setDeviceAliasName(d.getHostName());
                d.setActive(item.has("active") ? item.get("active").asInt() : 0);
                d.setInternetaccess(item.has("internetaccess") ? item.get("internetaccess").asInt() : 2);
                d.setBand(item.has("band") ? item.get("band").asText() : "");
                d.setOnlinetime(item.has("onlinetime") ? item.get("onlinetime").asInt() : 0);
                d.setSsid(item.has("ssid") ? item.get("ssid").asText() : "");
                d.setUserIpV6(item.has("userIpV6") ? item.get("userIpV6").asText() : "");
                d.setGroupName(item.has("groupName") ? item.get("groupName").asText() : "");
                d.setOsType(item.has("osType") ? item.get("osType").asText() : "");
                d.setConnectType(item.has("connectType") ? item.get("connectType").asInt() : 0);
                devices.add(d);
            }
        }
        return devices;
    }

    @Override
    public DeviceDetail getDetail(int idx) {
        JsonNode res = routerApi.getForJson("/api/v1/lua/OnlineUsers/getStaItem?idx=" + idx);
        if (res != null && res.has("data") && res.get("data").has("staItem")) {
            JsonNode item = res.get("data").get("staItem");
            DeviceDetail d = new DeviceDetail();
            d.setIdx(item.has("idx") ? item.get("idx").asInt() : 0);
            d.setMac(item.has("mac") ? item.get("mac").asText() : "");
            d.setIpaddr(item.has("ipaddr") ? item.get("ipaddr").asText() : "");
            d.setHostname(item.has("hostname") ? item.get("hostname").asText() : "");
            d.setBrand(item.has("brand") ? item.get("brand").asText() : "");
            d.setModel(item.has("model") ? item.get("model").asText() : "");
            d.setDevname(item.has("devname") ? item.get("devname").asText() : "");
            d.setActive(item.has("active") ? item.get("active").asInt() : 0);
            d.setInternetaccess(item.has("internetaccess") ? item.get("internetaccess").asInt() : 2);
            d.setActivetime(item.has("activetime") ? item.get("activetime").asText() : "");
            d.setOnlinetime(item.has("onlinetime") ? item.get("onlinetime").asInt() : 0);
            d.setBand(item.has("band") ? item.get("band").asText() : "");
            d.setSsid(item.has("ssid") ? item.get("ssid").asText() : "");
            d.setUploadspeed(item.has("uploadspeed") ? item.get("uploadspeed").asText() : "0.00");
            d.setDownloadspeed(item.has("downloadspeed") ? item.get("downloadspeed").asText() : "0.00");
            d.setUsbandwidth(item.has("usbandwidth") ? item.get("usbandwidth").asInt() : 0);
            d.setDsbandwidth(item.has("dsbandwidth") ? item.get("dsbandwidth").asInt() : 0);
            d.setBytesreceived(item.has("bytesreceived") ? item.get("bytesreceived").asText() : "0");
            d.setBytessent(item.has("bytessent") ? item.get("bytessent").asText() : "0");
            d.setDuplexmode(item.has("duplexmode") ? item.get("duplexmode").asText() : "");
            d.setNegorate(item.has("negorate") ? item.get("negorate").asInt() : 0);
            return d;
        }
        return null;
    }

    @Override
    public boolean setAccess(int idx, int internetaccess) {
        DeviceDetail detail = getDetail(idx);
        if (detail == null) return false;
        Map<String, Object> body = buildStaListBody(detail);
        body.put("internetaccess", internetaccess);
        JsonNode res = routerApi.postForJson("/api/v1/lua/OnlineUsers/set_sta_list", body);
        return res != null && res.has("code") && res.get("code").asInt() == 0;
    }

    @Override
    public boolean setSpeedLimit(int idx, int usbandwidth, int dsbandwidth, String uploadspeed, String downloadspeed) {
        DeviceDetail detail = getDetail(idx);
        if (detail == null) return false;
        Map<String, Object> body = buildStaItemBody(detail);
        body.put("usbandwidth", usbandwidth);
        body.put("dsbandwidth", dsbandwidth);
        body.put("uploadspeed", uploadspeed);
        body.put("downloadspeed", downloadspeed);
        JsonNode res = routerApi.postForJson("/api/v1/lua/OnlineUsers/setStaItem", body);
        return res != null && res.has("code") && res.get("code").asInt() == 0;
    }

    @Override
    public boolean addToBlacklist(String mac, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "deny");
        body.put("mac", mac);
        body.put("name", name);
        JsonNode res = routerApi.postForJson("/api/v1/lua/BlackWhiteList/addwirelessMacFilter", body);
        return res != null && res.has("code") && res.get("code").asInt() == 0;
    }

    @Override
    public boolean addToSupervision(String mac, String hostname) {
        // Supervision is managed locally — caller should use SupervisionService
        return true;
    }

    private Map<String, Object> buildStaListBody(DeviceDetail d) {
        Map<String, Object> body = new HashMap<>();
        body.put("userIp", d.getIpaddr());
        body.put("deviceAliasName", d.getHostname());
        body.put("active", d.getActive());
        body.put("rssi", 0);
        body.put("hostName", d.getHostname());
        body.put("idx", d.getIdx());
        body.put("mac", d.getMac());
        body.put("userIpV6", d.getIpaddr6());
        body.put("internetaccess", d.getInternetaccess());
        body.put("band", d.getBand());
        body.put("ssid", d.getSsid());
        body.put("onlinetime", d.getOnlinetime());
        return body;
    }

    private Map<String, Object> buildStaItemBody(DeviceDetail d) {
        Map<String, Object> body = new HashMap<>();
        body.put("idx", d.getIdx());
        body.put("mac", d.getMac());
        body.put("ipaddr", d.getIpaddr());
        body.put("hostname", d.getHostname());
        body.put("active", d.getActive());
        body.put("internetaccess", d.getInternetaccess());
        body.put("usbandwidth", d.getUsbandwidth());
        body.put("dsbandwidth", d.getDsbandwidth());
        body.put("uploadspeed", d.getUploadspeed());
        body.put("downloadspeed", d.getDownloadspeed());
        body.put("activetime", d.getActivetime());
        body.put("onlinetime", d.getOnlinetime());
        body.put("band", d.getBand());
        body.put("ssid", d.getSsid());
        body.put("brand", d.getBrand());
        body.put("model", d.getModel());
        body.put("devname", d.getDevname());
        body.put("port", d.getPort());
        body.put("connecttype", d.getConnecttype());
        body.put("duplexmode", d.getDuplexmode());
        body.put("negorate", d.getNegorate());
        body.put("ipaddr6", d.getIpaddr6());
        body.put("bytesreceived", d.getBytesreceived());
        body.put("bytessent", d.getBytessent());
        body.put("packetssent", d.getPacketssent());
        body.put("packetsreceived", d.getPacketsreceived());
        body.put("storageaccess", d.getStorageaccess());
        body.put("crcerror", d.getCrcerror());
        body.put("vmac", d.getVmac());
        return body;
    }
}
