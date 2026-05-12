package com.router.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.router.model.entity.ConnectionLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConnectionLogMapper extends BaseMapper<ConnectionLog> {
}
