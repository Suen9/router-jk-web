package com.router.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.router.mapper.WhitelistDeviceMapper;
import com.router.model.entity.WhitelistDevice;
import com.router.service.WhitelistService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class WhitelistServiceImpl implements WhitelistService {

    private final WhitelistDeviceMapper mapper;

    public WhitelistServiceImpl(WhitelistDeviceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<WhitelistDevice> list() {
        return mapper.selectList(null);
    }

    @Override
    public WhitelistDevice add(WhitelistDevice device) {
        mapper.insert(device);
        return device;
    }

    @Override
    public boolean remove(Long id) {
        return mapper.deleteById(id) > 0;
    }

    @Override
    public boolean isInWhitelist(String mac) {
        QueryWrapper<WhitelistDevice> qw = new QueryWrapper<>();
        qw.eq("mac", mac);
        return mapper.selectCount(qw) > 0;
    }
}
