package com.router.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.router.model.entity.PcCommand;
import org.apache.ibatis.annotations.Mapper;

/**
 * PC指令 Mapper — 提供 pc_command 表的基础CRUD操作
 */
@Mapper
public interface PcCommandMapper extends BaseMapper<PcCommand> {
}
