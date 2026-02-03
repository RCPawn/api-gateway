package com.rcpawn.service.impl;

import com.alibaba.cloud.nacos.NacosConfigManager;
import com.rcpawn.entity.sentinel.DegradeRuleEntity;
import com.rcpawn.entity.sentinel.FlowRuleEntity;
import com.rcpawn.entity.sentinel.ResourceRuleVO;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rcpawn.service.SentinelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SentinelServiceImpl implements SentinelService {

    @Autowired
    private NacosConfigManager nacosConfigManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Nacos 中的文件名
    private static final String FLOW_DATA_ID = "gateway-sentinel-flow.json";
    private static final String DEGRADE_DATA_ID = "gateway-sentinel-degrade.json";
    private static final String GROUP = "DEFAULT_GROUP";

    // --- 核心聚合逻辑 ---
    @Override
    public List<ResourceRuleVO> listResourceRules() throws Exception {
        // 1. 并行获取两份配置
        List<FlowRuleEntity> flowRules = getRules(FLOW_DATA_ID, new TypeReference<List<FlowRuleEntity>>() {});
        List<DegradeRuleEntity> degradeRules = getRules(DEGRADE_DATA_ID, new TypeReference<List<DegradeRuleEntity>>() {});

        // 2. 使用 Map 进行聚合 (Key = 资源名)
        Map<String, ResourceRuleVO> map = new HashMap<>();

        // 3. 填入限流规则
        for (FlowRuleEntity f : flowRules) {
            map.put(f.getResource(), new ResourceRuleVO(f.getResource(), f, null));
        }

        // 4. 填入降级规则 (如果资源已存在则合并，不存在则新增)
        for (DegradeRuleEntity d : degradeRules) {
            ResourceRuleVO vo = map.getOrDefault(d.getResource(), new ResourceRuleVO(d.getResource(), null, null));
            vo.setDegradeRule(d);
            map.put(d.getResource(), vo);
        }

        return new ArrayList<>(map.values());
    }

    @Override
    public void saveFlowRule(FlowRuleEntity rule) throws Exception {
        List<FlowRuleEntity> list = getRules(FLOW_DATA_ID, new TypeReference<List<FlowRuleEntity>>() {});
        // 移除旧规则 (同一资源名)
        list.removeIf(r -> r.getResource().equals(rule.getResource()));
        // 添加新规则
        list.add(rule);
        publish(FLOW_DATA_ID, list);
    }

    @Override
    public void saveDegradeRule(DegradeRuleEntity rule) throws Exception {
        List<DegradeRuleEntity> list = getRules(DEGRADE_DATA_ID, new TypeReference<List<DegradeRuleEntity>>() {});
        list.removeIf(r -> r.getResource().equals(rule.getResource()));
        list.add(rule);
        publish(DEGRADE_DATA_ID, list);
    }

    @Override
    public void deleteResource(String resource) throws Exception {
        // 删除限流
        List<FlowRuleEntity> flowList = getRules(FLOW_DATA_ID, new TypeReference<List<FlowRuleEntity>>() {});
        boolean fRemoved = flowList.removeIf(r -> r.getResource().equals(resource));
        if (fRemoved) publish(FLOW_DATA_ID, flowList);

        // 删除降级
        List<DegradeRuleEntity> degList = getRules(DEGRADE_DATA_ID, new TypeReference<List<DegradeRuleEntity>>() {});
        boolean dRemoved = degList.removeIf(r -> r.getResource().equals(resource));
        if (dRemoved) publish(DEGRADE_DATA_ID, degList);
    }

    // --- 私有工具方法 ---

    private <T> List<T> getRules(String dataId, TypeReference<List<T>> type) throws Exception {
        String json = nacosConfigManager.getConfigService().getConfig(dataId, GROUP, 5000);
        if (json == null || json.trim().isEmpty()) return new ArrayList<>();
        return objectMapper.readValue(json, type);
    }

    private void publish(String dataId, Object data) throws Exception {
        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        nacosConfigManager.getConfigService().publishConfig(dataId, GROUP, json);
    }
}