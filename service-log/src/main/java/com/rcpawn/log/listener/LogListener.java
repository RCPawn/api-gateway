package com.rcpawn.log.listener;

import com.rcpawn.common.entity.GatewayLogDTO;
import com.rcpawn.log.mapper.GatewayLogMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LogListener {

    @Autowired
    private GatewayLogMapper logMapper;

    @RabbitListener(queues = "gateway_log_queue")
    public void receiveLog(GatewayLogDTO log) {
        try {
            // 直接插入数据库
            logMapper.insert(log);
            System.out.println("💾 [Log服务] 日志入库成功: " + log.getPath());
        } catch (Exception e) {
            System.err.println("❌ [Log服务] 入库失败: " + e.getMessage());
        }
    }
}