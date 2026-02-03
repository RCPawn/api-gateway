package com.rcpawn.controller;

import com.rcpawn.common.util.Result;
import com.rcpawn.entity.sentinel.DegradeRuleEntity;
import com.rcpawn.entity.sentinel.FlowRuleEntity;
import com.rcpawn.entity.sentinel.ResourceRuleVO;
import com.rcpawn.service.SentinelService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/sentinel")
public class SentinelController {

    @Autowired
    private SentinelService sentinelService;

    /**
     * 获取聚合后的资源规则列表 (Dashboard 核心接口)
     */
    @GetMapping("/resources")
    public Result<List<ResourceRuleVO>> list() {
        try {
            return Result.success(sentinelService.listResourceRules());
        } catch (Exception e) {
            return Result.error("获取 Sentinel 规则失败: " + e.getMessage());
        }
    }

    /**
     * 保存/更新 限流规则
     */
    @PostMapping("/flow")
    public Result<String> saveFlow(@RequestBody FlowRuleEntity rule) {
        try {
            sentinelService.saveFlowRule(rule);
            return Result.success("限流规则更新成功");
        } catch (Exception e) {
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 保存/更新 降级规则
     */
    @PostMapping("/degrade")
    public Result<String> saveDegrade(@RequestBody DegradeRuleEntity rule) {
        try {
            sentinelService.saveDegradeRule(rule);
            return Result.success("降级规则更新成功");
        } catch (Exception e) {
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 删除整个资源的防护 (包括限流和降级)
     */
    @DeleteMapping("/resource")
    public Result<String> delete(@RequestParam String name) {
        try {
            sentinelService.deleteResource(name);
            return Result.success("资源防护已移除");
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }
}