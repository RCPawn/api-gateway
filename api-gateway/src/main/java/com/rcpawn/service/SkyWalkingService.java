package com.rcpawn.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * SkyWalkingService - 从 SkyWalking OAP 的 GraphQL 拉取拓扑并转为 ECharts 格式
 * 修复：避免 type 为 null 导致的 NullPointerException，并增强容错性
 */
@Service
public class SkyWalkingService {

    // SkyWalking OAP 地址（默认）
    @Value("${skywalking.oap-url:http://127.0.0.1:12800/graphql}")
    private String oapUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 获取全局拓扑结构
     */
    public Map<String, Object> getTopology() {
        // 1. 构造 GraphQL 查询语句（过去 15 分钟）
        String start = getTime(-15);
        String end = getTime(0);

        String queryGraphql = String.format(
                "{ \"query\": \"query queryTopology { topology: getGlobalTopology(duration: { start: \\\"%s\\\", end: \\\"%s\\\", step: MINUTE }) { nodes { id name type isReal } calls { source target detectPoints } } }\" }",
                start, end
        );

        try {
            // 2. 发送 HTTP POST 请求
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(queryGraphql, headers);

            String respStr = restTemplate.postForObject(oapUrl, entity, String.class);

            if (respStr == null) {
                System.err.println("SkyWalking OAP 返回空响应字符串");
                return new HashMap<>();
            }

            // 3. 解析 JSON
            JSONObject json = JSON.parseObject(respStr);
            if (json == null) {
                System.err.println("解析 SkyWalking OAP 返回为 null JSON");
                return new HashMap<>();
            }

            if (json.containsKey("errors")) {
                System.err.println("SkyWalking GraphQL Error: " + json.getString("errors"));
                return new HashMap<>();
            }

            JSONObject data = json.getJSONObject("data");
            if (data == null) {
                System.err.println("SkyWalking OAP 返回没有 data 字段: " + respStr);
                return new HashMap<>();
            }

            JSONObject topology = data.getJSONObject("topology");
            if (topology == null) {
                System.err.println("SkyWalking OAP 返回没有 topology 字段: " + data.toJSONString());
                return new HashMap<>();
            }

            JSONArray nodes = topology.getJSONArray("nodes");
            JSONArray calls = topology.getJSONArray("calls");

            return convertToECharts(nodes, calls);

        } catch (Exception e) {
            // 生产环境建议用 log.error
            System.err.println("连接 SkyWalking 失败: " + e.getMessage());
            e.printStackTrace();
            return new HashMap<>();
        }
    }

    /**
     * 数据转换：SkyWalking -> ECharts Graph
     * 目标输出：{ nodes: [...], links: [...] }
     */
    private Map<String, Object> convertToECharts(JSONArray swNodes, JSONArray swCalls) {
        List<Map<String, Object>> echartsNodes = new ArrayList<>();
        List<Map<String, Object>> echartsLinks = new ArrayList<>();
        Set<String> existNodes = new HashSet<>();

        try {
            // 处理节点（容错）
            if (swNodes != null) {
                for (int i = 0; i < swNodes.size(); i++) {
                    try {
                        JSONObject node = swNodes.getJSONObject(i);
                        if (node == null) continue;

                        String id = node.getString("id");
                        String name = node.getString("name");
                        String type = node.getString("type"); // 可能为 null

                        // 容错：若 id 为空则用 name；若都为空则生成临时 id（避免 ECharts 报错）
                        if ((id == null || id.isEmpty()) && (name != null && !name.isEmpty())) {
                            id = name;
                        }
                        if (id == null || id.isEmpty()) {
                            id = "node-" + UUID.randomUUID().toString();
                            System.err.println("Warning: found node without id/name, generated id=" + id + " at index=" + i);
                        }
                        if (name == null || name.isEmpty()) {
                            name = id;
                        }

                        Map<String, Object> map = new HashMap<>();
                        map.put("id", id);
                        map.put("name", name);

                        // 根据 type / name 做容错的样式映射（先检查 type 是否为 null）
                        boolean mapped = false;
                        if (type != null && !type.isEmpty()) {
                            String t = type.trim();
                            // 安全比较，避免 NPE
                            if (t.equalsIgnoreCase("SpringCloudGateway") || t.toLowerCase(Locale.ROOT).contains("gateway")) {
                                map.put("symbolSize", 60);
                                map.put("itemStyle", Collections.singletonMap("color", "#0ea5e9"));
                                mapped = true;
                            } else if (t.equalsIgnoreCase("Database") || t.equalsIgnoreCase("Mysql") || t.toLowerCase(Locale.ROOT).contains("mysql")) {
                                map.put("symbolSize", 30);
                                map.put("itemStyle", Collections.singletonMap("color", "#f59e0b"));
                                mapped = true;
                            } else if (t.equalsIgnoreCase("Redis") || t.toLowerCase(Locale.ROOT).contains("redis")) {
                                map.put("symbolSize", 30);
                                map.put("itemStyle", Collections.singletonMap("color", "#ef4444"));
                                mapped = true;
                            }
                        }

                        // 如果 type 没能决定样式，尝试用 name 做简易判断
                        if (!mapped) {
                            String lname = (name != null) ? name.toLowerCase(Locale.ROOT) : "";
                            if (lname.contains("gateway")) {
                                map.put("symbolSize", 60);
                                map.put("itemStyle", Collections.singletonMap("color", "#0ea5e9"));
                            } else if (lname.contains("mysql") || lname.contains("database") || lname.contains("db")) {
                                map.put("symbolSize", 30);
                                map.put("itemStyle", Collections.singletonMap("color", "#f59e0b"));
                            } else if (lname.contains("redis")) {
                                map.put("symbolSize", 30);
                                map.put("itemStyle", Collections.singletonMap("color", "#ef4444"));
                            } else {
                                map.put("symbolSize", 40);
                                map.put("itemStyle", Collections.singletonMap("color", "#10b981"));
                            }
                        }

                        // 将 type 暂存到 node 中，前端 tooltip 可用
                        if (type != null) {
                            map.put("type", type);
                        }

                        // 额外 detail 字段兼容
                        if (node.containsKey("detail")) map.put("detail", node.getString("detail"));
                        else if (node.containsKey("tooltip")) map.put("detail", node.getString("tooltip"));

                        echartsNodes.add(map);
                        existNodes.add(id);
                    } catch (Exception exNode) {
                        // 单条节点解析错，记录并继续
                        System.err.println("解析 SkyWalking 节点时发生异常（跳过该节点）: " + exNode.getMessage());
                        exNode.printStackTrace();
                    }
                }
            }

            // 处理连线（容错）
            if (swCalls != null) {
                for (int i = 0; i < swCalls.size(); i++) {
                    try {
                        JSONObject call = swCalls.getJSONObject(i);
                        if (call == null) continue;

                        String source = call.getString("source");
                        String target = call.getString("target");

                        // 一些 schema 可能使用 from/to 等字段
                        if ((source == null || source.isEmpty()) && call.containsKey("from")) source = call.getString("from");
                        if ((target == null || target.isEmpty()) && call.containsKey("to")) target = call.getString("to");

                        if (source == null || target == null) {
                            // 跳过不完整的连接
                            System.err.println("Warning: 跳过不完整的 call (缺 source/target) index=" + i + " content=" + call);
                            continue;
                        }

                        if (existNodes.contains(source) && existNodes.contains(target)) {
                            Map<String, Object> link = new HashMap<>();
                            link.put("source", source);
                            link.put("target", target);

                            // 可选：根据 call 内容设置 lineStyle（若无则使用默认）
                            Map<String, Object> lineStyle = new HashMap<>();
                            // 可尝试读取 weight / throughput 字段作为宽度
                            Double weight = null;
                            try {
                                if (call.containsKey("weight")) weight = call.getDouble("weight");
                                else if (call.containsKey("throughput")) weight = call.getDouble("throughput");
                            } catch (Exception ignore) {}

                            if (weight == null) weight = 2.0;
                            boolean slow = false;
                            if (call.containsKey("status")) {
                                String status = call.getString("status");
                                if (status != null && (status.equalsIgnoreCase("slow") || status.equalsIgnoreCase("error"))) slow = true;
                            }

                            String color = slow ? "#f43f5e" : "#5eead4";
                            double width = Math.max(1.0, Math.min(8.0, weight));
                            lineStyle.put("color", color);
                            lineStyle.put("width", width);
                            lineStyle.put("curveness", 0.15);
                            lineStyle.put("opacity", 0.8);

                            link.put("lineStyle", lineStyle);

                            if (call.containsKey("metric")) link.put("desc", call.getString("metric"));
                            else if (call.containsKey("avgLatency")) link.put("desc", "latency=" + call.getString("avgLatency"));

                            echartsLinks.add(link);
                        }
                    } catch (Exception exCall) {
                        System.err.println("解析 SkyWalking call 时发生异常（跳过该 call）: " + exCall.getMessage());
                        exCall.printStackTrace();
                    }
                }
            }
        } catch (Exception ex) {
            // 守护型捕获，避免抛到上层
            System.err.println("convertToECharts 发生异常: " + ex.getMessage());
            ex.printStackTrace();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nodes", echartsNodes);
        result.put("links", echartsLinks);
        return result;
    }

    /**
     * 生成 SkyWalking 需要的时间格式 (yyyy-MM-dd HHmm)
     */
    private String getTime(int minuteOffset) {
        return LocalDateTime.now().plusMinutes(minuteOffset)
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm"));
    }
}