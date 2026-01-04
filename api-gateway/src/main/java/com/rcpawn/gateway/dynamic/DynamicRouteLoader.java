package com.rcpawn.gateway.dynamic;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.fastjson2.JSON;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.event.RefreshRoutesEvent;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Nacos 动态路由监听器
 * 核心逻辑：Nacos发生改变 -> 获取配置 -> 更新网关路由 -> 发布刷新事件
 */
@Slf4j
@Component
public class DynamicRouteLoader implements ApplicationEventPublisherAware {

    @Autowired
    private NacosConfigManager nacosConfigManager;

    @Autowired
    private RouteDefinitionWriter routeDefinitionWriter; // 网关提供的增删改路由接口

    private ApplicationEventPublisher publisher;

    // Nacos 配置的 DataId，必须和 YAML 里配置的一致
    private static final String DATA_ID = "api-gateway-routes.json";
    private static final String GROUP = "DEFAULT_GROUP";

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.publisher = applicationEventPublisher;
    }

    /**
     * 项目启动时，先拉取一次配置
     */
    @PostConstruct
    public void init() {
        try {
            // 1. 获取 Nacos Config Service
            String configInfo = nacosConfigManager.getConfigService()
                    .getConfigAndSignListener(DATA_ID, GROUP, 5000, new Listener() {
                        @Override
                        public Executor getExecutor() {
                            return null;
                        }

                        @Override
                        public void receiveConfigInfo(String configInfo) {
                            log.info("监听到 Nacos 路由配置发生变化，开始刷新...");
                            updateRoutes(configInfo);
                        }
                    });
            
            // 2. 第一次初始化
            if (configInfo != null) {
                log.info("初始化 Nacos 路由配置...");
                updateRoutes(configInfo);
            }
        } catch (NacosException e) {
            log.error("Nacos 路由配置初始化失败", e);
        }
    }

    /**
     * 解析 JSON 并更新路由
     */
    public void updateRoutes(String configInfo) {
        try {
            // 1. 解析 JSON 为 RouteDefinition List
            List<RouteDefinition> routeDefinitions = JSON.parseArray(configInfo, RouteDefinition.class);

            if (routeDefinitions == null) {
                return;
            }

            // 2. 这里为了简单，先清空旧路由再添加新路由（生产环境建议做 Diff 差异更新）
            // 注意：RouteDefinitionWriter 没有直接的 clear 方法，通常是先 delete 再 save
            // 但由于此时无法获取旧路由 ID，这里简化为直接覆盖逻辑，
            // 实际上 Gateway 底层是内存 Map，Save 同 ID 会覆盖。
            
            for (RouteDefinition definition : routeDefinitions) {
                // 更新路由
                routeDefinitionWriter.save(Mono.just(definition)).subscribe();
            }

            // 3. 发布刷新事件，让网关生效
            this.publisher.publishEvent(new RefreshRoutesEvent(this));
            log.info("路由更新成功，共加载 {} 条路由", routeDefinitions.size());

        } catch (Exception e) {
            log.error("路由配置解析失败", e);
        }
    }
}