package com.router.service;

import com.router.model.entity.PcCommand;
import com.router.model.vo.PcCommandVO;

import java.util.List;

/**
 * PC指令服务接口 — 管理指令的增删查改、状态流转（pending→sent→success/failed）
 */
public interface PcCommandService {

    /** 创建指令 */
    PcCommand create(Long pcDeviceId, String commandType, String params);

    /** 根据ID查询指令 */
    PcCommand getById(Long id);

    /** 查询指令列表（按PC设备、类型、状态筛选，分页） */
    List<PcCommandVO> query(Long pcDeviceId, String commandType, String status, int page, int size);

    /** 统计总记录数 */
    int count(Long pcDeviceId, String commandType, String status);

    /** 获取PC待执行的指令（置为sent状态，用于Agent轮询） */
    PcCommand pollPending(Long pcDeviceId);

    /** 更新指令状态为完成（Agent上报结果） */
    boolean complete(Long id, boolean success, String result, String errorMessage);
}
