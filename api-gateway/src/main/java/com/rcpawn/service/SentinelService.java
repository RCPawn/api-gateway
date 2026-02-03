package com.rcpawn.service;

import com.rcpawn.entity.sentinel.DegradeRuleEntity;
import com.rcpawn.entity.sentinel.FlowRuleEntity;
import com.rcpawn.entity.sentinel.ResourceRuleVO;

import java.util.List;

public interface SentinelService {
    // 获取聚合后的资源规则列表 (给卡片页面用)
    List<ResourceRuleVO> listResourceRules() throws Exception;
    
    // 保存限流规则
    void saveFlowRule(FlowRuleEntity rule) throws Exception;
    
    // 保存降级规则
    void saveDegradeRule(DegradeRuleEntity rule) throws Exception;
    
    // 删除某个资源的所有规则
    void deleteResource(String resource) throws Exception;
}