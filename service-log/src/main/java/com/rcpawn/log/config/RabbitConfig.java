package com.rcpawn.log.config;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    
    @Bean
    public Queue logQueue() {
        return new Queue("gateway_log_queue", true); // true = 持久化队列
    }

    // 这一步很关键！配置 JSON 序列化器，否则收到的是乱码
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}