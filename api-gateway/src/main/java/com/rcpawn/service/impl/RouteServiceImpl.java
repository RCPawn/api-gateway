package com.rcpawn.service.impl;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.rcpawn.entity.GatewayRouteEntity;
import com.rcpawn.service.RouteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class RouteServiceImpl implements RouteService {

    @Autowired
    private NacosConfigManager nacosConfigManager;

    private static final String DATA_ID = "api-gateway-routes.json";
    private static final String GROUP = "DEFAULT_GROUP";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public List<GatewayRouteEntity> listRoutes() throws Exception {
        String configInfo = nacosConfigManager.getConfigService().getConfig(DATA_ID, GROUP, 5000);
        if (configInfo == null) return new ArrayList<>();
        return objectMapper.readValue(configInfo, new TypeReference<List<GatewayRouteEntity>>() {});
    }

    @Override
    public void updateRoute(GatewayRouteEntity route) throws Exception {
        List<GatewayRouteEntity> list = listRoutes();
        // 如果是修改，先删旧的；如果是新增，本身就没有旧的（无副作用）
        list.removeIf(r -> r.getId().equals(route.getId()));
        list.add(route);
        publish(list);
    }

    @Override
    public void deleteRoute(String id) throws Exception {
        List<GatewayRouteEntity> list = listRoutes();
        boolean removed = list.removeIf(r -> r.getId().equals(id));
        if (removed) {
            publish(list);
        } else {
            throw new RuntimeException("路由ID不存在");
        }
    }

    private void publish(List<GatewayRouteEntity> list) throws Exception {
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(list);
        nacosConfigManager.getConfigService().publishConfig(DATA_ID, GROUP, json);
    }
}