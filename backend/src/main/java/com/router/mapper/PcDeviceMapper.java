package com.router.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.router.model.entity.PcDevice;
import org.apache.ibatis.annotations.Mapper;

/**
 * PC设备 Mapper — 提供 pc_device 表的基础CRUD操作
 */
@Mapper
public interface PcDeviceMapper extends BaseMapper<PcDevice> {
}
