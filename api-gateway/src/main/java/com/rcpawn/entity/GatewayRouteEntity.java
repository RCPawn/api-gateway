package com.rcpawn.entity;

import lombok.Data;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 用于映射 Nacos 中 JSON 结构的实体类
 */
@Data
public class GatewayRouteEntity {
    private String id;
    private String uri;
    private int order;
    private List<PredicateEntity> predicates = new ArrayList<>();
    private List<FilterEntity> filters = new ArrayList<>();

    // ================= 内部类结构 =================
    @Data
    public static class PredicateEntity {
        private String name;
        private Map<String, String> args = new LinkedHashMap<>();
    }

    @Data
    public static class FilterEntity {
        private String name;
        private Map<String, String> args = new LinkedHashMap<>();
    }
}