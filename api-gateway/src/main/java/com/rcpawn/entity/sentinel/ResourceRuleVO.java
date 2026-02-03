package com.rcpawn.entity.sentinel;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResourceRuleVO {
    private String resource;                // 卡片标题
    private FlowRuleEntity flowRule;        // 限流信息 (没有则为null)
    private DegradeRuleEntity degradeRule;  // 降级信息 (没有则为null)
}