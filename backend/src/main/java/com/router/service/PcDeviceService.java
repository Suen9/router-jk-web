package com.router.service;

import com.router.model.entity.PcDevice;
import com.router.model.vo.PcDeviceVO;

import java.util.List;

/**
 * PC设备服务接口 — 管理受控PC设备的注册、状态跟踪、信息更新和在线状态判断
 */
public interface PcDeviceService {

    /** 注册PC设备（Agent首次运行时调用），生成并返回agentToken */
    String register(String mac, String hostname, String ip, String osVersion, String agentVersion);

    /** 心跳更新（Agent定时上报） */
    boolean heartbeat(Long deviceId, String ip);

    /** 根据MAC查找设备 */
    PcDevice findByMac(String mac);

    /** 根据ID查找设备 */
    PcDevice getById(Long id);

    /** 获取所有设备列表（含在线状态） */
    List<PcDeviceVO> list(String keyword);

    /** 手动添加PC设备 */
    PcDevice add(String mac, String hostname, String remark);

    /** 更新PC设备信息 */
    boolean update(Long id, String hostname, String remark);

    /** 删除PC设备 */
    boolean remove(Long id);

    /** 验证Agent Token是否有效 */
    PcDevice validateToken(String token);

    /** 下线离线设备（心跳超时，由调度器调用） */
    int markOffline(int timeoutSeconds);

    /**
     * 将PC设备置为离线状态（Agent连接断开时调用）
     */
    boolean markOfflineById(Long deviceId);
}
