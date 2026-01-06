package com.rcpawn.controller;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rcpawn.entity.GatewayRouteEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/admin/routes")
public class RouteController {

    @Autowired
    private NacosConfigManager nacosConfigManager;

    // Nacos 配置的 ID 和 Group，必须和你 bootstrap.yml 里配的一致，也和你 Nacos 里的文件名一致
    private static final String DATA_ID = "api-gateway-routes.json";
    private static final String GROUP = "DEFAULT_GROUP";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 1. 获取当前路由列表
     */
    @GetMapping
    public List<GatewayRouteEntity> list() throws Exception {
        // 从 Nacos 拉取最新的 Config 内容
        String configInfo = nacosConfigManager.getConfigService().getConfig(DATA_ID, GROUP, 5000);
        if (configInfo == null) {
            return new ArrayList<>();
        }
        // 将 JSON 字符串转为 List 对象
        return objectMapper.readValue(configInfo, new TypeReference<List<GatewayRouteEntity>>() {});
    }

    /**
     * 2. 新增或更新路由
     */
    @PostMapping
    public String add(@RequestBody GatewayRouteEntity route) throws Exception {
        // 1. 先查出来现有的
        List<GatewayRouteEntity> list = list();

        // 2. 检查是否已存在，存在则删除旧的（相当于更新）
        list.removeIf(r -> r.getId().equals(route.getId()));

        // 3. 加入新的
        list.add(route);

        // 4. 转回 JSON 字符串
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(list);

        // 5. 发布回 Nacos
        boolean success = nacosConfigManager.getConfigService().publishConfig(DATA_ID, GROUP, json);

        return success ? "更新成功，Nacos已接收，网关即将刷新" : "更新失败";
    }
    
    /**
     * 3. 删除路由
     */
    @DeleteMapping("/{id}")
    public String delete(@PathVariable String id) throws Exception {
        List<GatewayRouteEntity> list = list();
        boolean removed = list.removeIf(r -> r.getId().equals(id));
        
        if (removed) {
            String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(list);
            nacosConfigManager.getConfigService().publishConfig(DATA_ID, GROUP, json);
            return "删除成功";
        }
        return "ID不存在";
    }
}