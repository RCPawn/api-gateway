package com.rcpawn.controller;

import com.rcpawn.common.util.Result;
import com.rcpawn.entity.GatewayRouteEntity;
import com.rcpawn.service.RouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/routes")
public class RouteController {

    @Autowired
    private RouteService routeService;

    @GetMapping
    public Result<List<GatewayRouteEntity>> list() {
        try {
            return Result.success(routeService.listRoutes());
        } catch (Exception e) {
            return Result.error("获取路由失败: " + e.getMessage());
        }
    }

    @PostMapping
    public Result<String> save(@RequestBody GatewayRouteEntity route) {
        try {
            routeService.updateRoute(route);
            return Result.success("保存成功");
        } catch (Exception e) {
            return Result.error("保存失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<String> delete(@PathVariable String id) {
        try {
            routeService.deleteRoute(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }
}