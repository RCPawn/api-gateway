package com.rcpawn.log.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rcpawn.common.entity.GatewayLogDTO;
import com.rcpawn.log.mapper.GatewayLogMapper;
import com.rcpawn.log.service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LogServiceImpl implements LogService {

    @Autowired
    private GatewayLogMapper logMapper;

    @Override
    public Page<GatewayLogDTO> pageQuery(int page, int size, String path) {
        Page<GatewayLogDTO> pageParam = new Page<>(page, size);
        
        LambdaQueryWrapper<GatewayLogDTO> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(GatewayLogDTO::getRequestTime); // 按时间倒序
        
        if (path != null && !path.isEmpty()) {
            wrapper.like(GatewayLogDTO::getPath, path);
        }
        
        return logMapper.selectPage(pageParam, wrapper);
    }
}