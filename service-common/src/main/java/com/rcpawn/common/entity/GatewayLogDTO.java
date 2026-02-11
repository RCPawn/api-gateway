package com.rcpawn.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

@Data
@TableName("gateway_log")
public class GatewayLogDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String traceId;
    private String userId;
    private String ip;
    private String path;
    private String method;
    private Integer status;
    private Long responseTime;
    private Date requestTime;
}