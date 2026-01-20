package com.rcpawn.log.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rcpawn.common.entity.GatewayLogDTO;

public interface LogService {
    Page<GatewayLogDTO> pageQuery(int page, int size, String path);
}