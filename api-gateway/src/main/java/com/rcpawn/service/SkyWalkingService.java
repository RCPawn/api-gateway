package com.rcpawn.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SkyWalkingService {

    @Value("${skywalking.oap-url:http://127.0.0.1:12800/graphql}")
    private String oapUrl;

    /** 本进程在 Spring 中的名字，用于把 Agent 默认名映射成驾驶舱期望的展示名 */
    @Value("${spring.application.name:api-gateway}")
    private String localApplicationName;

    /**
     * 逗号分隔：OAP 上若仍显示 Agent 默认服务名，则按网关节点处理（避免 User→「默认名」被误判为 APP 后改写出自环边被丢弃）
     */
    @Value("${skywalking.topology.gateway-service-name-aliases:Your_ApplicationName}")
    private String gatewayServiceNameAliases;

    private final RestTemplate restTemplate = new RestTemplate();

    // 本地缓存 (TTL 5秒)
    private final Cache<String, Map<String, Object>> topologyCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.SECONDS)
            .maximumSize(1)
            .build();

    public Map<String, Object> getTopology() {
        Map<String, Object> cached = topologyCache.getIfPresent("topology");
        if (cached != null) {
            return cached;
        }

        Map<String, Object> freshData = fetchFromSkyWalking();
        if (!freshData.isEmpty()) {
            topologyCache.put("topology", freshData);
        }
        return freshData;
    }

    private Map<String, Object> fetchFromSkyWalking() {
        // 只查最近 3 分钟
        String start = getTime(-3);
        String end = getTime(0);

        String queryGraphql = String.format(
                "{ \"query\": \"query queryTopology { topology: getGlobalTopology(duration: { start: \\\"%s\\\", end: \\\"%s\\\", step: MINUTE }) { nodes { id name type isReal } calls { source target detectPoints } } }\" }",
                start, end
        );

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(queryGraphql, headers);

            String respStr = restTemplate.postForObject(oapUrl, entity, String.class);

            if (respStr == null) return Collections.emptyMap();

            JSONObject json = JSON.parseObject(respStr);
            if (json == null) return Collections.emptyMap();
            if (json.containsKey("errors")) {
                log.warn("SkyWalking GraphQL errors: {}", json.get("errors"));
                return Collections.emptyMap();
            }

            JSONObject data = json.getJSONObject("data");
            if (data == null) return Collections.emptyMap();

            JSONObject topology = data.getJSONObject("topology");
            if (topology == null) return Collections.emptyMap();

            return convertToECharts(topology.getJSONArray("nodes"), topology.getJSONArray("calls"));

        } catch (Exception e) {
            log.error("Failed to fetch topology: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private Map<String, Object> convertToECharts(JSONArray swNodes, JSONArray swCalls) {
        List<Map<String, Object>> echartsNodes = new ArrayList<>();
        List<Map<String, Object>> echartsLinks = new ArrayList<>();

        Map<String, String> idToNameMap = new HashMap<>();
        // 记录节点类型，方便后续连线判断
        Map<String, String> idToTypeMap = new HashMap<>();
        Set<String> validNodeIds = new HashSet<>();

        String gatewayId = null;

        // 1. 处理节点
        if (swNodes != null) {
            for (int i = 0; i < swNodes.size(); i++) {
                JSONObject node = swNodes.getJSONObject(i);
                String id = node.getString("id");
                String name = node.getString("name");
                String type = node.getString("type");

                if (id == null) id = name;
                if (name == null) name = id;
                if (id == null) continue;

                idToNameMap.put(id, name);
                validNodeIds.add(id);

                Map<String, Object> map = new HashMap<>();
                map.put("id", id);
                map.put("name", name);
                map.put("type", type); // 传给前端以备不时之需

                // 设置样式并获取归一化类型 (DB/REDIS/GATEWAY/USER/APP)
                String normalizedType = setNodeStyleAndGetType(map, name, type);
                idToTypeMap.put(id, normalizedType);

                // 找到网关ID (用于后续修正连线)
                if ("GATEWAY".equals(normalizedType)) {
                    gatewayId = id;
                }

                echartsNodes.add(map);
            }
        }

        // 2. 处理连线 (核心修正逻辑)
        if (swCalls != null) {
            for (int i = 0; i < swCalls.size(); i++) {
                JSONObject call = swCalls.getJSONObject(i);
                String sourceId = call.getString("source");
                String targetId = call.getString("target");

                if (sourceId == null || targetId == null) continue;

                if (validNodeIds.contains(sourceId) && validNodeIds.contains(targetId)) {
                    String sType = idToTypeMap.get(sourceId);
                    String tType = idToTypeMap.get(targetId);

                    // 🛡️ 连线修正逻辑 🛡️
                    // 如果源头是 User，且目标是普通微服务 (非网关/非DB/非Redis)
                    // 说明这是采样丢失导致的“直连错觉”，强行把源头改成 Gateway
                    if ("USER".equals(sType) && "APP".equals(tType)) {
                        if (gatewayId != null) {
                            sourceId = gatewayId; // 采样丢失时：User -> 下游 纠正为 Gateway -> 下游
                        }
                        // 未识别到网关时仍保留 User -> 服务，避免驾驶舱「无线」
                    }

                    // 避免自我连接
                    if (sourceId.equals(targetId)) continue;

                    Map<String, Object> link = new HashMap<>();
                    link.put("source", sourceId);
                    link.put("target", targetId);

                    Map<String, Object> lineStyle = new HashMap<>();
                    lineStyle.put("curveness", 0.2);
                    lineStyle.put("color", "#5eead4");
                    link.put("lineStyle", lineStyle);

                    echartsLinks.add(link);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", echartsNodes);
        result.put("links", echartsLinks);
        return result;
    }

    /**
     * 节点样式映射 & 类型归一化
     * 返回类型枚举: GATEWAY, REDIS, DB, USER, APP
     */
    private String setNodeStyleAndGetType(Map<String, Object> map, String name, String type) {
        String lowerName = name != null ? name.toLowerCase() : "";
        String lowerType = type != null ? type.toLowerCase() : "";
        String normalizedType = "APP"; // 默认

        // 0. Agent 未传 service_name 时 SkyWalking 默认名，按网关处理并替换展示名（与 spring.application.name 一致）
        if (isGatewayServiceAlias(name)) {
            map.put("name", localApplicationName);
            map.put("symbolSize", 60);
            map.put("itemStyle", Collections.singletonMap("color", "#0ea5e9"));
            return "GATEWAY";
        }

        // 1. Gateway
        if (lowerName.contains("gateway") || lowerType.contains("gateway")) {
            map.put("symbolSize", 60);
            map.put("itemStyle", Collections.singletonMap("color", "#0ea5e9"));
            normalizedType = "GATEWAY";
        }
        // 2. Redis
        else if (lowerName.contains("redis") || lowerType.contains("redis") || lowerType.contains("lettuce") || lowerType.contains("jedis")) {
            map.put("symbolSize", 30);
            map.put("itemStyle", Collections.singletonMap("color", "#ef4444"));
            normalizedType = "REDIS";
        }
        // 3. Database
        else if (lowerName.contains("mysql") || lowerType.contains("mysql") || lowerType.contains("database") || lowerType.contains("h2")) {
            map.put("symbolSize", 30);
            map.put("itemStyle", Collections.singletonMap("color", "#f59e0b"));
            normalizedType = "DB";
        }
        // 4. User
        else if (lowerName.equals("user")) {
            map.put("symbolSize", 30);
            map.put("itemStyle", Collections.singletonMap("color", "#a8a29e"));
            normalizedType = "USER";
        }
        // 5. 普通微服务
        else {
            map.put("symbolSize", 45);
            map.put("itemStyle", Collections.singletonMap("color", "#10b981"));
            normalizedType = "APP";
        }
        return normalizedType;
    }

    private Set<String> gatewayAliasSet() {
        if (gatewayServiceNameAliases == null || gatewayServiceNameAliases.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(gatewayServiceNameAliases.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toSet());
    }

    private boolean isGatewayServiceAlias(String name) {
        return name != null && gatewayAliasSet().contains(name.toLowerCase(Locale.ROOT));
    }

    private String getTime(int minuteOffset) {
        return LocalDateTime.now().plusMinutes(minuteOffset)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm"));
    }
}
