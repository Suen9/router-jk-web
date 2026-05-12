package com.router.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.router.mapper.PcDeviceMapper;
import com.router.model.entity.PcDevice;
import com.router.model.vo.PcDeviceVO;
import com.router.service.PcDeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PC设备服务实现 — 处理设备注册、心跳、状态跟踪和在线判断
 */
@Service
public class PcDeviceServiceImpl implements PcDeviceService {

    private static final Logger log = LoggerFactory.getLogger(PcDeviceServiceImpl.class);

    private final PcDeviceMapper mapper;

    public PcDeviceServiceImpl(PcDeviceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String register(String mac, String hostname, String ip, String osVersion, String agentVersion) {
        PcDevice existing = findByMac(mac);
        if (existing != null) {
            // 已注册设备：更新信息 + 生成新token
            existing.setHostname(hostname);
            existing.setIp(ip);
            existing.setOsVersion(osVersion);
            existing.setAgentVersion(agentVersion);
            existing.setStatus("online");
            existing.setLastHeartbeat(LocalDateTime.now());
            String token = generateToken();
            existing.setAgentToken(token);
            mapper.updateById(existing);
            log.info("[PC注册] 更新已有设备 id={} mac={} hostname={}", existing.getId(), mac, hostname);
            return token;
        }

        // 新设备注册
        String token = generateToken();
        PcDevice device = new PcDevice();
        device.setMac(mac);
        device.setHostname(hostname);
        device.setIp(ip);
        device.setOsVersion(osVersion);
        device.setAgentVersion(agentVersion);
        device.setStatus("online");
        device.setAgentToken(token);
        device.setLastHeartbeat(LocalDateTime.now());
        mapper.insert(device);
        log.info("[PC注册] 新设备 id={} mac={} hostname={}", device.getId(), mac, hostname);
        return token;
    }

    @Override
    public boolean heartbeat(Long deviceId, String ip) {
        PcDevice device = mapper.selectById(deviceId);
        if (device == null) {
            log.warn("[PC心跳] 设备不存在 id={}", deviceId);
            return false;
        }
        device.setLastHeartbeat(LocalDateTime.now());
        device.setIp(ip);
        device.setStatus("online");
        mapper.updateById(device);
        return true;
    }

    @Override
    public PcDevice findByMac(String mac) {
        QueryWrapper<PcDevice> qw = new QueryWrapper<>();
        qw.eq("mac", mac);
        return mapper.selectOne(qw);
    }

    @Override
    public PcDevice getById(Long id) {
        return mapper.selectById(id);
    }

    @Override
    public List<PcDeviceVO> list(String keyword) {
        QueryWrapper<PcDevice> qw = new QueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            qw.like("hostname", keyword)
                    .or().like("ip", keyword)
                    .or().like("mac", keyword);
        }
        qw.orderByDesc("last_heartbeat");
        List<PcDevice> devices = mapper.selectList(qw);

        // 计算在线状态：lastHeartbeat 超过30秒前视为离线
        LocalDateTime now = LocalDateTime.now();
        List<PcDeviceVO> vos = new ArrayList<>();
        for (PcDevice d : devices) {
            PcDeviceVO vo = new PcDeviceVO();
            vo.setId(d.getId());
            vo.setHostname(d.getHostname());
            vo.setIp(d.getIp());
            vo.setMac(d.getMac());
            vo.setOsVersion(d.getOsVersion());
            vo.setAgentVersion(d.getAgentVersion());
            vo.setLastHeartbeat(d.getLastHeartbeat());
            vo.setRemark(d.getRemark());
            vo.setCreateTime(d.getCreateTime());

            // 实时计算在线状态
            if (d.getLastHeartbeat() != null && d.getLastHeartbeat().plusSeconds(30).isAfter(now)) {
                vo.setStatus("online");
            } else {
                vo.setStatus("offline");
            }
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public PcDevice add(String mac, String hostname, String remark) {
        PcDevice device = new PcDevice();
        device.setMac(mac);
        device.setHostname(hostname);
        device.setRemark(remark);
        device.setStatus("offline");
        mapper.insert(device);
        log.info("[PC管理] 手动添加设备 id={} mac={} hostname={}", device.getId(), mac, hostname);
        return device;
    }

    @Override
    public boolean update(Long id, String hostname, String remark) {
        PcDevice device = mapper.selectById(id);
        if (device == null) return false;
        if (hostname != null) device.setHostname(hostname);
        if (remark != null) device.setRemark(remark);
        mapper.updateById(device);
        log.info("[PC管理] 更新设备 id={} hostname={}", id, hostname);
        return true;
    }

    @Override
    public boolean remove(Long id) {
        int rows = mapper.deleteById(id);
        log.info("[PC管理] 删除设备 id={} result={}", id, rows > 0);
        return rows > 0;
    }

    @Override
    public PcDevice validateToken(String token) {
        if (token == null || token.isEmpty()) return null;
        QueryWrapper<PcDevice> qw = new QueryWrapper<>();
        qw.eq("agent_token", token);
        return mapper.selectOne(qw);
    }

    @Override
    public int markOffline(int timeoutSeconds) {
        LocalDateTime threshold = LocalDateTime.now().minusSeconds(timeoutSeconds);
        QueryWrapper<PcDevice> qw = new QueryWrapper<>();
        qw.eq("status", "online")
                .lt("last_heartbeat", threshold);
        List<PcDevice> devices = mapper.selectList(qw);
        for (PcDevice d : devices) {
            d.setStatus("offline");
            mapper.updateById(d);
        }
        if (!devices.isEmpty()) {
            log.info("[PC离线] 标记 {} 台设备离线（心跳超时>{}秒）", devices.size(), timeoutSeconds);
        }
        return devices.size();
    }

    @Override
    public boolean markOfflineById(Long deviceId) {
        PcDevice device = mapper.selectById(deviceId);
        if (device == null) return false;
        device.setStatus("offline");
        mapper.updateById(device);
        return true;
    }

    /** 生成64位Hex随机Token */
    private String generateToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
