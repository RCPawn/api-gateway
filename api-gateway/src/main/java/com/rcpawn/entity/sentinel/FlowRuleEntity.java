package com.rcpawn.entity.sentinel;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true) // 👈 加上这一行！关键！
public class FlowRuleEntity {
    private String resource;        // 资源名 (必填)
    private String limitApp;        // 来源应用 (默认 default)
    private Integer grade;          // 阈值类型: 1=QPS, 0=线程数
    private Double count;           // 阈值 (如 100)
    private Integer strategy;       // 流控模式: 0=直接, 1=关联, 2=链路
    private Integer controlBehavior;// 流控效果: 0=快速失败, 1=WarmUp, 2=排队等待
    private Integer warmUpPeriodSec;// 预热时长 (当 controlBehavior=1 时有效)
    private Integer maxQueueingTimeMs; // 排队超时 (当 controlBehavior=2 时有效)
}