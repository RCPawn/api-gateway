package com.rcpawn.log.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rcpawn.common.entity.GatewayLogDTO;
import com.rcpawn.common.util.Result;
import com.rcpawn.log.service.LogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/logs")
public class LogController {

    @Autowired
    private LogService logService;

    @GetMapping
    public Result<Page<GatewayLogDTO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String path) {

        Page<GatewayLogDTO> result = logService.pageQuery(page, size, path);
        return Result.success(result);
    }
}