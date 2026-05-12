package com.router.controller;

import com.router.common.BaseResponse;
import com.router.common.ResultUtils;
import com.router.model.entity.PcCommand;
import com.router.model.entity.PcDevice;
import com.router.service.PcCommandService;
import com.router.service.PcDeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * PC Agent 通信 Controller — Agent 注册、心跳、指令轮询和结果上报接口。
 * 所有接口需要 Header X-Agent-Token 鉴权。
 */
@RestController
@RequestMapping("/api/pc/agent")
public class PcAgentController {

    private static final Logger log = LoggerFactory.getLogger(PcAgentController.class);

    private final PcDeviceService pcDeviceService;
    private final PcCommandService pcCommandService;

    public PcAgentController(PcDeviceService pcDeviceService, PcCommandService pcCommandService) {
        this.pcDeviceService = pcDeviceService;
        this.pcCommandService = pcCommandService;
    }

    /**
     * Agent注册：首次运行时调用，返回 agent_token
     */
    @PostMapping("/register")
    public BaseResponse<Map<String, Object>> register(@RequestBody Map<String, String> params) {
        String mac = params.get("mac");
        String hostname = params.get("hostname");
        String ip = params.get("ip");
        String osVersion = params.get("osVersion");
        String agentVersion = params.get("agentVersion");

        if (mac == null || mac.isEmpty()) {
            return ResultUtils.error(40000, "MAC地址不能为空");
        }

        String token = pcDeviceService.register(mac, hostname, ip, osVersion, agentVersion);
        PcDevice device = pcDeviceService.findByMac(mac);

        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", device.getId());
        result.put("agentToken", token);
        return ResultUtils.success(result);
    }

    /**
     * Agent心跳上报
     */
    @PostMapping("/heartbeat")
    public BaseResponse<Object> heartbeat(
            @RequestHeader("X-Agent-Token") String token,
            @RequestBody Map<String, String> params) {
        PcDevice device = pcDeviceService.validateToken(token);
        if (device == null) {
            return ResultUtils.error(40300, "无效的Agent Token");
        }
        pcDeviceService.heartbeat(device.getId(), params.getOrDefault("ip", device.getIp()));
        return ResultUtils.success(null);
    }

    /**
     * Agent轮询拉取待执行指令
     */
    @GetMapping("/poll")
    public BaseResponse<Object> poll(@RequestHeader("X-Agent-Token") String token) {
        PcDevice device = pcDeviceService.validateToken(token);
        if (device == null) {
            return ResultUtils.error(40300, "无效的Agent Token");
        }
        PcCommand cmd = pcCommandService.pollPending(device.getId());
        if (cmd == null) {
            return ResultUtils.success(null);
        }
        Map<String, Object> result = new HashMap<>();
        result.put("commandId", cmd.getId());
        result.put("commandType", cmd.getCommandType());
        result.put("params", cmd.getParams());
        return ResultUtils.success(result);
    }

    /**
     * Agent上报指令执行结果
     */
    @PostMapping("/report")
    public BaseResponse<Object> report(
            @RequestHeader("X-Agent-Token") String token,
            @RequestBody Map<String, Object> params) {
        PcDevice device = pcDeviceService.validateToken(token);
        if (device == null) {
            return ResultUtils.error(40300, "无效的Agent Token");
        }

        Long commandId = params.get("commandId") != null
                ? Long.valueOf(params.get("commandId").toString()) : null;
        boolean success = Boolean.TRUE.equals(params.get("success"));
        String result = (String) params.get("result");
        String errorMessage = (String) params.get("errorMessage");

        if (commandId == null) {
            return ResultUtils.error(40000, "commandId不能为空");
        }

        pcCommandService.complete(commandId, success, result, errorMessage);
        return ResultUtils.success(null);
    }
}
