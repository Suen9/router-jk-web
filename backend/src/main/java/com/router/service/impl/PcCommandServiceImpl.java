package com.router.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.router.mapper.PcCommandMapper;
import com.router.mapper.PcDeviceMapper;
import com.router.model.entity.PcCommand;
import com.router.model.entity.PcDevice;
import com.router.model.vo.PcCommandVO;
import com.router.service.PcCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * PC指令服务实现 — 管理指令的完整生命周期：创建、轮询、执行结果上报
 */
@Service
public class PcCommandServiceImpl implements PcCommandService {

    private static final Logger log = LoggerFactory.getLogger(PcCommandServiceImpl.class);

    private final PcCommandMapper commandMapper;
    private final PcDeviceMapper deviceMapper;

    public PcCommandServiceImpl(PcCommandMapper commandMapper, PcDeviceMapper deviceMapper) {
        this.commandMapper = commandMapper;
        this.deviceMapper = deviceMapper;
    }

    @Override
    public PcCommand create(Long pcDeviceId, String commandType, String params) {
        PcCommand cmd = new PcCommand();
        cmd.setPcDeviceId(pcDeviceId);
        cmd.setCommandType(commandType);
        cmd.setParams(params);
        cmd.setStatus("pending");
        cmd.setCreatedBy("web");
        commandMapper.insert(cmd);
        log.info("[PC指令] 创建指令 id={} deviceId={} type={}", cmd.getId(), pcDeviceId, commandType);
        return cmd;
    }

    @Override
    public PcCommand getById(Long id) {
        return commandMapper.selectById(id);
    }

    @Override
    public List<PcCommandVO> query(Long pcDeviceId, String commandType, String status, int page, int size) {
        QueryWrapper<PcCommand> qw = new QueryWrapper<>();
        if (pcDeviceId != null) {
            qw.eq("pc_device_id", pcDeviceId);
        }
        if (commandType != null && !commandType.isEmpty()) {
            qw.eq("command_type", commandType);
        }
        if (status != null && !status.isEmpty()) {
            qw.eq("status", status);
        }
        qw.orderByDesc("create_time");

        Page<PcCommand> pageResult = commandMapper.selectPage(new Page<>(page, size), qw);
        List<PcCommandVO> vos = new ArrayList<>();
        for (PcCommand cmd : pageResult.getRecords()) {
            PcCommandVO vo = toVO(cmd);
            vos.add(vo);
        }
        return vos;
    }

    @Override
    public int count(Long pcDeviceId, String commandType, String status) {
        QueryWrapper<PcCommand> qw = new QueryWrapper<>();
        if (pcDeviceId != null) qw.eq("pc_device_id", pcDeviceId);
        if (commandType != null && !commandType.isEmpty()) qw.eq("command_type", commandType);
        if (status != null && !status.isEmpty()) qw.eq("status", status);
        return commandMapper.selectCount(qw).intValue();
    }

    @Override
    public PcCommand pollPending(Long pcDeviceId) {
        QueryWrapper<PcCommand> qw = new QueryWrapper<>();
        qw.eq("pc_device_id", pcDeviceId)
                .eq("status", "pending")
                .orderByAsc("create_time")
                .last("LIMIT 1");
        PcCommand cmd = commandMapper.selectOne(qw);
        if (cmd != null) {
            cmd.setStatus("sent");
            cmd.setSendTime(LocalDateTime.now());
            commandMapper.updateById(cmd);
            log.info("[PC指令] 下发指令 id={} type={} to deviceId={}", cmd.getId(), cmd.getCommandType(), pcDeviceId);
        }
        return cmd;
    }

    @Override
    public boolean complete(Long id, boolean success, String result, String errorMessage) {
        PcCommand cmd = commandMapper.selectById(id);
        if (cmd == null) {
            log.warn("[PC指令] 完成上报: 指令不存在 id={}", id);
            return false;
        }
        cmd.setStatus(success ? "success" : "failed");
        cmd.setResult(result);
        cmd.setErrorMessage(errorMessage);
        cmd.setCompleteTime(LocalDateTime.now());
        commandMapper.updateById(cmd);
        log.info("[PC指令] 指令完成 id={} status={}", id, cmd.getStatus());
        return true;
    }

    /** 将指令实体转为VO，填充关联设备名 */
    private PcCommandVO toVO(PcCommand cmd) {
        PcCommandVO vo = new PcCommandVO();
        vo.setId(cmd.getId());
        vo.setPcDeviceId(cmd.getPcDeviceId());
        vo.setCommandType(cmd.getCommandType());
        vo.setParams(cmd.getParams());
        vo.setStatus(cmd.getStatus());
        vo.setResult(cmd.getResult());
        vo.setErrorMessage(cmd.getErrorMessage());
        vo.setCreatedBy(cmd.getCreatedBy());
        vo.setCreateTime(cmd.getCreateTime());
        vo.setSendTime(cmd.getSendTime());
        vo.setCompleteTime(cmd.getCompleteTime());

        // 填充设备名
        PcDevice device = deviceMapper.selectById(cmd.getPcDeviceId());
        if (device != null) {
            vo.setDeviceHostname(device.getHostname());
        }
        return vo;
    }
}
