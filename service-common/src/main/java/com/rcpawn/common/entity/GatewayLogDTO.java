package com.rcpawn.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
// 👇【核心修复】明确指定表名，防止它乱转译
@TableName("gateway_log")
public class GatewayLogDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    // 不写 id，因为 id 是自增的，插入时不需要传
    
    private String traceId;      // SkyWalking TraceID
    private String userId;       // 用户 ID
    private String ip;           // 访问 IP
    private String path;         // 请求路径
    private String method;       // 请求方法
    private Integer status;      // 响应状态码
    private Long responseTime;   // 耗时 (ms)
    private Date requestTime;    // 请求发生时间
    
    // createTime 也不用写，数据库会自动生成
}