package com.rcpawn.log.task;

import com.rcpawn.log.mapper.GatewayLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class LogCleanupTask {

    @Autowired
    private GatewayLogMapper logMapper;

    /**
     * 每天凌晨 3 点执行一次
     * 清理 7 天前的日志
     */
    @Scheduled(cron = "0 0 3 * * ?") 
    public void cleanOldLogs() {
        System.out.println("🧹 [定时任务] 开始清理过期日志...");
        
        // 计算 7 天前的时间点
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        
        // 执行删除 SQL
        // 注意：MyBatis-Plus 的 delete 需要构造 Wrapper
        // 这里的逻辑是：DELETE FROM gateway_log WHERE request_time < sevenDaysAgo
        int deletedCount = logMapper.delete(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.rcpawn.common.entity.GatewayLogDTO>()
                .lt(com.rcpawn.common.entity.GatewayLogDTO::getRequestTime, sevenDaysAgo)
        );

        System.out.println("✅ [定时任务] 清理完成，共删除日志条数: " + deletedCount);
    }
}