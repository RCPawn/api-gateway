package com.rcpawn.entity.sentinel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // 👈 加上这一行！关键！
public class DegradeRuleEntity {
    private String resource;        // 资源名
    private Integer grade;          // 熔断策略: 0=慢调用(RT), 1=异常比例, 2=异常数
    private Double count;           // 阈值 (如 慢调用时长 or 比例)
    private Integer timeWindow;     // 熔断时长 (秒)
    private Integer minRequestAmount; // 最小请求数 (触发熔断的门槛)
    private Double slowRatioThreshold; // 慢调用比例阈值 (仅策略0有效)
    private Integer statIntervalMs;    // 统计时长 (毫秒)
}