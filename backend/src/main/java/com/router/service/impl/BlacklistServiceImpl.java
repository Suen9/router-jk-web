package com.router.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.router.model.dto.BlacklistItem;
import com.router.service.BlacklistService;
import com.router.service.RouterApiService;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BlacklistServiceImpl implements BlacklistService {

    private final RouterApiService routerApi;

    public BlacklistServiceImpl(RouterApiService routerApi) {
        this.routerApi = routerApi;
    }

    @Override
    public List<BlacklistItem> getBlacklist() {
        JsonNode res = routerApi.getForJson("/api/v1/lua/BlackWhiteList/wirelessMacFilter?type=&firstIn=true");
        List<BlacklistItem> list = new ArrayList<>();
        if (res != null && res.has("data") && res.get("data").has("macList")) {
            String type = res.get("data").has("type") ? res.get("data").get("type").asText() : "deny";
            for (JsonNode item : res.get("data").get("macList")) {
                BlacklistItem bi = new BlacklistItem();
                bi.setMac(item.has("mac") ? item.get("mac").asText() : "");
                bi.setName(item.has("name") ? item.get("name").asText() : "");
                bi.setStatus("已生效");
                bi.setSource("手动添加");
                list.add(bi);
            }
        }
        return list;
    }

    @Override
    public boolean add(String mac, String name) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "deny");
        body.put("mac", mac);
        body.put("name", name);
        JsonNode res = routerApi.postForJson("/api/v1/lua/BlackWhiteList/addwirelessMacFilter", body);
        return res != null && res.has("code") && res.get("code").asInt() == 0;
    }

    @Override
    public boolean isInBlacklist(String mac) {
        return getBlacklist().stream().anyMatch(b -> b.getMac().equalsIgnoreCase(mac));
    }

    @Override
    public boolean delete(List<String> macList) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "deny");
        body.put("macList", macList);
        JsonNode res = routerApi.postForJson("/api/v1/lua/BlackWhiteList/deletewirelessMacFilter", body);
        return res != null && res.has("code") && res.get("code").asInt() == 0;
    }
}
